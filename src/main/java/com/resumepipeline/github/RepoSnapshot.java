package com.resumepipeline.github;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

/**
 * Every text file of one repo at one commit, held in memory. Built from a single tarball
 * download instead of one contents-API call per file, so mapping a whole repo costs one
 * request and cannot trip GitHub's rate limit halfway through.
 *
 * <p>Vendored, lockfile, and binary paths are skipped ({@link RepoFilter}), as are files
 * over {@link #MAX_FILE_BYTES} and anything past {@link #MAX_TOTAL_BYTES} of text.
 */
public final class RepoSnapshot {

    static final int MAX_FILE_BYTES = GithubService.MAX_FILE_BYTES;
    static final long MAX_TOTAL_BYTES = 25L * 1024 * 1024;

    private final Map<String, String> files;
    private final boolean truncated;

    public RepoSnapshot(Map<String, String> files, boolean truncated) {
        this.files = Collections.unmodifiableMap(new TreeMap<>(files));
        this.truncated = truncated;
    }

    /** path -> content, sorted by path. */
    public Map<String, String> files() { return files; }
    public boolean truncated() { return truncated; }
    public String read(String path) { return files.get(path); }

    /**
     * Reads a GitHub tarball (gzip'd ustar with pax headers). The archive's single top-level
     * directory ({@code owner-repo-sha/}) is stripped so paths match the contents API.
     */
    public static RepoSnapshot fromTarGz(InputStream gz) throws IOException {
        Map<String, String> out = new TreeMap<>();
        boolean truncated = false;
        long total = 0;
        try (InputStream in = new GZIPInputStream(gz)) {
            byte[] header = new byte[512];
            String paxPath = null;
            while (readFully(in, header)) {
                if (isZeroBlock(header)) break;
                String name = cString(header, 0, 100);
                String prefix = cString(header, 345, 155);
                long size = Long.parseLong(cString(header, 124, 12).trim().isEmpty() ? "0" : cString(header, 124, 12).trim(), 8);
                char type = (char) header[156];
                byte[] body = readBody(in, size);

                if (type == 'x') {                     // pax extended header for the NEXT entry
                    paxPath = paxValue(new String(body, StandardCharsets.UTF_8), "path");
                    continue;
                }
                if (type == 'L') {                     // GNU long name for the NEXT entry
                    paxPath = cString(body, 0, body.length);
                    continue;
                }
                String full = paxPath != null ? paxPath : (prefix.isEmpty() ? name : prefix + "/" + name);
                paxPath = null;
                if (type != '0' && type != '\0') continue; // dirs, symlinks, global headers

                int slash = full.indexOf('/');
                String path = slash >= 0 ? full.substring(slash + 1) : full;
                if (path.isEmpty() || RepoFilter.isNoise(path) || size > MAX_FILE_BYTES || looksBinary(body)) continue;
                if (total + size > MAX_TOTAL_BYTES) { truncated = true; continue; }
                total += size;
                out.put(path, new String(body, StandardCharsets.UTF_8));
            }
        }
        return new RepoSnapshot(out, truncated);
    }

    private static byte[] readBody(InputStream in, long size) throws IOException {
        long padded = (size + 511) / 512 * 512;
        if (size > MAX_FILE_BYTES) {                   // skip without buffering
            in.skipNBytes(padded);
            return new byte[0];
        }
        byte[] data = new byte[(int) size];
        if (!readFully(in, data) && size > 0) throw new IOException("Truncated tar entry");
        in.skipNBytes(padded - size);
        return data;
    }

    private static boolean readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) return off == buf.length;
            off += n;
        }
        return true;
    }

    private static boolean isZeroBlock(byte[] b) {
        for (byte x : b) if (x != 0) return false;
        return true;
    }

    private static String cString(byte[] b, int off, int len) {
        int end = off;
        while (end < off + len && end < b.length && b[end] != 0) end++;
        return new String(b, off, end - off, StandardCharsets.UTF_8);
    }

    /** pax records are "LEN key=value\n". */
    static String paxValue(String pax, String key) {
        for (String rec : pax.split("\n")) {
            int sp = rec.indexOf(' ');
            if (sp < 0) continue;
            String kv = rec.substring(sp + 1);
            if (kv.startsWith(key + "=")) return kv.substring(key.length() + 1);
        }
        return null;
    }

    private static boolean looksBinary(byte[] b) {
        int n = Math.min(b.length, 8000);
        for (int i = 0; i < n; i++) if (b[i] == 0) return true;
        return false;
    }
}
