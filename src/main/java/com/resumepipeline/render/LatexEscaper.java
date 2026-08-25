package com.resumepipeline.render;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Escapes arbitrary text (including LLM output) for safe inclusion in LaTeX.
 *
 * Two-pass design:
 *   1. Escape special chars ({ } $ & % # _ ~ ^ \). Uses a sentinel for backslash
 *      so escapes don't cascade.
 *   2. Apply typographic replacements (Unicode quotes, dashes, ellipsis, NBSP).
 *      These emit literal LaTeX and run AFTER escaping so their backslashes
 *      survive verbatim.
 */
@Component
public class LatexEscaper {

    private static final String BACKSLASH_SENTINEL = "RPBSLASHSENTINEL";

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final String LDQUO  = String.valueOf((char) 0x201C);
    private static final String RDQUO  = String.valueOf((char) 0x201D);
    private static final String LSQUO  = String.valueOf((char) 0x2018);
    private static final String RSQUO  = String.valueOf((char) 0x2019);
    private static final String ENDASH = String.valueOf((char) 0x2013);
    private static final String EMDASH = String.valueOf((char) 0x2014);
    private static final String HELLIP = String.valueOf((char) 0x2026);
    private static final String NBSP   = String.valueOf((char) 0x00A0);

    public String escape(String input) {
        if (input == null) return "";

        // Collapse all whitespace (incl. newlines) to single spaces: a blank line inside a
        // \textbf{...}/\textit{...} argument becomes \par and aborts the LaTeX compile with
        // "Paragraph ended before \text@command was complete".
        String s = WHITESPACE.matcher(input).replaceAll(" ").trim();

        // --- Pass 1: escape LaTeX special chars ---
        s = s.replace("\\", BACKSLASH_SENTINEL);
        s = s.replace("{", "\\{").replace("}", "\\}");
        s = s.replace("$", "\\$");
        s = s.replace("&", "\\&");
        s = s.replace("%", "\\%");
        s = s.replace("#", "\\#");
        s = s.replace("_", "\\_");
        s = s.replace("~", "\\textasciitilde{}");
        s = s.replace("^", "\\textasciicircum{}");
        s = s.replace(BACKSLASH_SENTINEL, "\\textbackslash{}");

        // --- Pass 2: typographic replacements (emit literal LaTeX) ---
        s = s.replace(LDQUO, "``").replace(RDQUO, "''");
        s = s.replace(LSQUO, "`").replace(RSQUO, "'");
        s = s.replace(EMDASH, "---").replace(ENDASH, "--");
        s = s.replace(HELLIP, "\\ldots{}");
        s = s.replace(NBSP, "~");

        return s;
    }
}
