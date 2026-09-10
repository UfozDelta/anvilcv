package com.resumepipeline.bullet;

import com.resumepipeline.application.ApplicationRenderer;
import com.resumepipeline.render.LatexRenderer;
import com.resumepipeline.render.PdfCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ground-truth line-fit check for bullets: typesets every candidate through the SAME
 * {@code \resumeItem} / nested-itemize macros and margins resume.tex actually renders bullets
 * with, the preamble sliced live from the real template ({@link LatexRenderer#preambleOf}) so
 * this can never hand-drift from production geometry.
 *
 * <p>Reads back two real TeX registers instead of guessing from character count:
 * <ul>
 *   <li>{@code \prevgraf} -- the exact number of lines the paragraph broke into;</li>
 *   <li>the last line's natural (ink) width via the standard "extract the last line, then
 *       repack it at its natural width" trick -- repacking discards the {@code \raggedright}
 *       fill glue that would otherwise make every line report as exactly {@code \linewidth}.</li>
 * </ul>
 *
 * <p>Costs one tectonic compile per call (every candidate batched into it), so this is for
 * batch/persist-time gates (bullet refit, bank save) -- never the per-retry generation loop in
 * {@code BaseLlmClient}, which needs an instant answer and stays on
 * {@link com.resumepipeline.llm.BulletTextRules#decide}. A compile failure or a value that
 * looks implausible never blocks the caller: {@link #measure} returns an empty map and callers
 * fall back to the char-count check, same policy as the fit/recruiter LLM passes elsewhere in
 * this codebase -- a missing real measurement is a nuisance, not a reason to lose a bullet.
 *
 * <p><b>Partially verified.</b> The {@code \typeout} marker format and {@link #MARKER} regex have
 * been confirmed against a real tectonic compile -- the original version silently swallowed the
 * space before {@code lw=} (TeX eats the space after a control word, {@code \rplines} and
 * {@code \the\wd3} included, regardless of what they expand to), which meant {@link #parse}
 * matched nothing, ever; see the {@code \space} tokens in {@link #buildTex}. The box arithmetic
 * itself (raggedright glue behavior, the natural-width repack, single-line edge cases) is still
 * unverified against a real bullet's paragraph-breaking -- sanity-check {@code measure()}'s
 * fill ratios against a compiled PDF before trusting this in production.
 */
@Component
public class BulletLineMeasurer {

    private static final Logger log = LoggerFactory.getLogger(BulletLineMeasurer.class);

    public record Measured(int lines, double lastLineFill) {}

    /** Below this fraction of the line width, a rendered line reads as sparse padding. */
    static final double MIN_FILL = 0.55;

    private final LatexRenderer latex;
    private final ApplicationRenderer appRenderer;
    private final PdfCompiler compiler;

    public BulletLineMeasurer(LatexRenderer latex, ApplicationRenderer appRenderer, PdfCompiler compiler) {
        this.latex = latex;
        this.appRenderer = appRenderer;
        this.compiler = compiler;
    }

    /**
     * Measures every candidate in one compile. Keys are caller-supplied ids (bullet UUID
     * strings) -- kept opaque here since the only requirement is that they survive a TeX
     * {@code \typeout} line unescaped, which a UUID's alphanumeric-plus-dash form does.
     */
    public Map<String, Measured> measure(Map<String, String> textsById) {
        if (textsById.isEmpty()) return Map.of();
        String tex = buildTex(textsById);
        PdfCompiler.Result r = compiler.compile(tex);
        if (!r.success()) {
            log.warn("Bullet line measurement compile failed, falling back to char-count: {}", r.error());
            return Map.of();
        }
        Map<String, Measured> out = parse(r.log());
        if (out.size() != textsById.size()) {
            log.warn("Bullet line measurement returned {}/{} bullets, falling back to char-count for the rest.",
                    out.size(), textsById.size());
        }
        return out;
    }

    /** True when a measured line count is exactly 1 or 2 and every rendered line is well filled. */
    public boolean isCleanFit(Measured m) {
        if (m == null) return false;
        if (m.lines() < 1 || m.lines() > 2) return false;
        return m.lastLineFill() >= MIN_FILL;
    }

    String buildTex(Map<String, String> textsById) {
        String preamble = latex.preambleOf("template/resume.tex");
        StringBuilder sb = new StringBuilder(preamble);
        sb.append("\\begin{document}\n");
        sb.append("\\newlength{\\bulletlw}\n");
        // Capture the real linewidth from the exact nesting bullets render inside:
        // \resumeSubHeadingListStart (leftmargin 0.15in) containing an item whose
        // \resumeItemListStart (another leftmargin 0.15in) holds the bullet itself.
        sb.append("\\begin{itemize}[leftmargin=0.15in, label={}]\n\\item\n");
        sb.append("\\begin{itemize}[leftmargin=0.15in, itemsep=0pt, parsep=0pt, topsep=4pt, partopsep=0pt]\n");
        sb.append("\\global\\setlength{\\bulletlw}{\\linewidth}\n");
        sb.append("\\end{itemize}\n\\end{itemize}\n");

        for (var e : textsById.entrySet()) {
            String id = e.getKey();
            String escaped = appRenderer.escapeRich(e.getValue());
            // \prevgraf must be captured INSIDE this \vbox's group, before its closing brace --
            // confirmed against a real compile that reading it after the box closes reports a
            // stale value from whatever paragraph was last broken in the OUTER vertical list,
            // not this one. \vbox{...} opens a local group, and \prevgraf is an assignable
            // internal quantity like any other: the paragraph builder's assignment to it during
            // this box's construction is local to that group and is rolled back the moment the
            // box closes. \xdef (a GLOBAL edef), not \edef, is what lets the frozen value
            // survive the group closing even though the \prevgraf register itself reverts.
            sb.append("\\setbox0=\\vbox{\\hsize=\\bulletlw \\raggedright\\small\\noindent ")
              .append(escaped).append("\\par\n\\xdef\\rplines{\\the\\prevgraf}}\n");
            // Extract the last line, then repack it at natural width: a line box built by
            // the paragraph breaker is always set to exactly \hsize (raggedright pads the
            // slack with a \hskip 0pt plus 1fil), so the width of the box as extracted would
            // just report \linewidth for every bullet. Re-boxing with no explicit target
            // width forces TeX to re-measure from the natural widths of the contents, and an
            // infinite-stretch glue's natural component is 0, so this yields real ink width.
            sb.append("\\setbox1=\\vbox{\\unvcopy0 \\global\\setbox2=\\lastbox}\n");
            sb.append("\\setbox3=\\hbox{\\unhbox2}\n");
            // Two layered TeX gotchas, both confirmed against a real tectonic compile:
            // (1) \the\wd3 used directly in the typeout line is unsafe -- \wd takes an
            //     explicit register NUMBER, and while TeX scans that number for more digits
            //     it expands the next token to check; an inserted \space gets consumed right
            //     there as the number's own terminator and never reaches the output. Freezing
            //     it into a plain \edef'd macro first (\rpwidth) sidesteps the register-number
            //     scan entirely -- by the time \rpwidth is used below, it's just a macro name.
            // (2) A plain control word -- \rplines and \rpwidth included, regardless of what
            //     they expand to -- swallows one trailing literal space when the tokenizer
            //     reads it. Without the explicit \space token here the log prints
            //     "lines=7width=12.34ptlw=56.78pt", which MARKER below can never match.
            //     \space is itself a control word expanding to a literal space character, so
            //     there is no following space character for that rule to eat.
            sb.append("\\edef\\rpwidth{\\the\\wd3}\n");
            sb.append("\\typeout{RPMEASURE id=").append(id)
              .append(" lines=\\rplines\\space width=\\rpwidth\\space lw=\\the\\bulletlw}\n");
        }
        sb.append("\\end{document}\n");
        return sb.toString();
    }

    Map<String, Measured> parse(String log) {
        Map<String, Measured> out = new LinkedHashMap<>();
        if (log == null) return out;
        Matcher m = MARKER.matcher(log);
        while (m.find()) {
            try {
                int lines = Integer.parseInt(m.group("lines"));
                double width = Double.parseDouble(m.group("width"));
                double lw = Double.parseDouble(m.group("lw"));
                double fill = lw > 0 ? Math.min(1.0, width / lw) : 0.0;
                out.put(m.group("id"), new Measured(lines, fill));
            } catch (NumberFormatException ignored) {
                // A malformed marker leaves that bullet absent from the map; the caller's
                // size check surfaces it as a fallback-to-char-count case, never a crash.
            }
        }
        return out;
    }

    private static final Pattern MARKER = Pattern.compile(
            "RPMEASURE id=(?<id>\\S+) lines=(?<lines>\\d+) width=(?<width>[-\\d.]+)pt lw=(?<lw>[-\\d.]+)pt");
}
