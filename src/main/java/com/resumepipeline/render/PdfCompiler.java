package com.resumepipeline.render;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Component
public class PdfCompiler {

    private final String binary;
    private final int timeoutSeconds;

    public PdfCompiler(
            @Value("${tectonic.binary:tectonic}") String binary,
            @Value("${tectonic.timeout-seconds:30}") int timeoutSeconds) {
        this.binary = binary;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Caps concurrent tectonic processes. Each compile forks a process and holds the
     * calling request thread for up to {@code tectonic.timeout-seconds}; the bullet
     * preview lets a user fire these off freely, so without a bound a few clicks can
     * pin every core.
     */
    private static final Semaphore SLOTS = new Semaphore(2);

    public Result compile(String latexSource) {
        try {
            SLOTS.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.failure("Interrupted waiting for a compile slot", "");
        }
        try {
            return compileNow(latexSource);
        } finally {
            SLOTS.release();
        }
    }

    private Result compileNow(String latexSource) {
        Path tmp = null;
        try {
            tmp = Files.createTempDirectory("rp-tex-" + UUID.randomUUID());
            Path tex = tmp.resolve("in.tex");
            Files.writeString(tex, latexSource, StandardCharsets.UTF_8);

            ProcessBuilder pb = new ProcessBuilder(
                    binary, "--outdir", tmp.toString(), "--keep-logs", "--chatter", "minimal",
                    tex.toString()
            ).redirectErrorStream(true);

            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return Result.failure("tectonic timed out after " + timeoutSeconds + "s", output);
            }
            if (p.exitValue() != 0) {
                String log = readIfExists(tmp.resolve("in.log"));
                return Result.failure("tectonic exit " + p.exitValue(), output + "\n--- in.log ---\n" + log);
            }

            byte[] pdf = Files.readAllBytes(tmp.resolve("in.pdf"));
            return Result.success(pdf, output);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return Result.failure(e.getClass().getSimpleName() + ": " + e.getMessage(), "");
        } finally {
            if (tmp != null) deleteRecursively(tmp);
        }
    }

    private static String readIfExists(Path p) {
        try { return Files.exists(p) ? Files.readString(p, StandardCharsets.UTF_8) : ""; }
        catch (IOException e) { return ""; }
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted((a, b) -> b.getNameCount() - a.getNameCount())
             .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        } catch (IOException ignored) {}
    }

    public record Result(boolean success, byte[] pdf, String log, String error) {
        public static Result success(byte[] pdf, String log) { return new Result(true, pdf, log, null); }
        public static Result failure(String err, String log) { return new Result(false, null, log, err); }
    }
}
