package com.resumepipeline.render;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LatexRenderer {

    private final LatexEscaper escaper;

    public LatexRenderer(LatexEscaper escaper) {
        this.escaper = escaper;
    }

    /**
     * Fills the template by replacing {{PLACEHOLDER}} tokens with escaped values.
     * Values are passed through LatexEscaper.escape(). Pass already-rendered LaTeX
     * fragments via the {@link #renderRaw} variant instead.
     */
    public String render(String templateClasspath, Map<String, String> values) {
        String tex = dropEmptySections(readTemplate(templateClasspath), values);
        for (var entry : values.entrySet()) {
            String token = "{{" + entry.getKey() + "}}";
            tex = tex.replace(token, escaper.escape(entry.getValue()));
        }
        return tex;
    }

    /** Like render() but values are inserted verbatim (caller is responsible for escaping). */
    public String renderRaw(String templateClasspath, Map<String, String> values) {
        String tex = dropEmptySections(readTemplate(templateClasspath), values);
        for (var entry : values.entrySet()) {
            String token = "{{" + entry.getKey() + "}}";
            tex = tex.replace(token, entry.getValue() == null ? "" : entry.getValue());
        }
        return tex;
    }

    /**
     * A %%SECTION:KEY%% ... %%ENDSECTION%% block is removed entirely when KEY has no
     * content, and unwrapped when it does.
     *
     * <p>The section macros expand to \begin{itemize}, and LaTeX aborts on an itemize
     * with zero \item ("Something's wrong--perhaps a missing \item"). Leaving the block
     * in with an empty body fails the whole compile, so a resume with no projects — or
     * no experience, or no education — produced no PDF at all.
     */
    private String dropEmptySections(String tex, Map<String, String> values) {
        Matcher m = SECTION_BLOCK.matcher(tex);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String body = m.group(2);
            String value = values.get(key);
            boolean empty = value == null || value.isBlank();
            m.appendReplacement(out, Matcher.quoteReplacement(empty ? "" : body));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static final Pattern SECTION_BLOCK = Pattern.compile(
            "%%SECTION:([A-Z_]+)%%\\R(.*?)%%ENDSECTION%%\\R?", Pattern.DOTALL);

    private String readTemplate(String classpath) {
        try (var in = getClass().getClassLoader().getResourceAsStream(classpath)) {
            if (in == null) throw new IllegalArgumentException("Template not found: " + classpath);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read template " + classpath, e);
        }
    }
}
