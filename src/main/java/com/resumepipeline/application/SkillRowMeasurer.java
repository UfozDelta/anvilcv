package com.resumepipeline.application;

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
 * Ground-truth width check for the Technical Skills row, mirroring
 * {@link com.resumepipeline.bullet.BulletLineMeasurer}: typesets {@code \skillrowtext} -- the
 * same macro resume.tex renders skill rows with, at the same {@code \small} size {@code
 * \skillrow} tries first -- inside the real single-level itemize nesting the skills block
 * renders in, and reads back the natural ink width plus the real {@code \linewidth} from XeTeX
 * itself instead of estimating from character count.
 *
 * <p>Used to grow each skill category past its selected/floor set with more raw profile
 * skills, stretching the row toward {@code \linewidth} without tripping {@code \skillrow}'s own
 * footnotesize/scriptsize shrink fallback. A compile failure never blocks rendering -- the
 * caller keeps whatever count it already had (see {@code BulletSelector.fillSkills}), same
 * policy as the bullet measurer.
 *
 * <p><b>Was silently a no-op.</b> The original {@code \typeout} line put {@code \the\wd0}
 * directly against a following space and {@code lw=}; TeX swallows the space after a control
 * word regardless of what it expands to, so the log printed {@code width=12.34ptlw=56.78pt}
 * and {@link #MARKER} never matched a single row -- every call fell through the "compile failure
 * never blocks rendering" path and every category silently stayed at its floor-filled count.
 * Confirmed against a real tectonic compile and fixed with an explicit {@code \space} token;
 * see {@link #buildTex}.
 */
@Component
public class SkillRowMeasurer {

    private static final Logger log = LoggerFactory.getLogger(SkillRowMeasurer.class);

    public record Row(String label, String itemsJoined) {}

    public record Measured(double width, double linewidth) {
        public boolean fits(double marginFraction) {
            return linewidth > 0 && width <= linewidth * marginFraction;
        }
    }

    private final LatexRenderer latex;
    private final ApplicationRenderer appRenderer;
    private final PdfCompiler compiler;

    public SkillRowMeasurer(LatexRenderer latex, ApplicationRenderer appRenderer, PdfCompiler compiler) {
        this.latex = latex;
        this.appRenderer = appRenderer;
        this.compiler = compiler;
    }

    /**
     * Measures every candidate row in one compile. Keys are caller-supplied ids -- kept opaque
     * here since the only requirement is that they survive a TeX {@code \typeout} line
     * unescaped (an alphanumeric-plus-underscore id such as {@code "languages_7"} qualifies).
     *
     * @param rows id -> (category label as raw LaTeX, comma-joined item text, unescaped)
     */
    public Map<String, Measured> measure(Map<String, Row> rows) {
        if (rows.isEmpty()) return Map.of();
        String tex = buildTex(rows);
        PdfCompiler.Result r = compiler.compile(tex);
        if (!r.success()) {
            log.warn("Skill row measurement compile failed, keeping existing skill counts: {}", r.error());
            return Map.of();
        }
        Map<String, Measured> out = parse(r.log());
        if (out.size() != rows.size()) {
            log.warn("Skill row measurement returned {}/{} rows, keeping existing counts for the rest.",
                    out.size(), rows.size());
        }
        return out;
    }

    String buildTex(Map<String, Row> rows) {
        String preamble = latex.preambleOf("template/resume.tex");
        StringBuilder sb = new StringBuilder(preamble);
        sb.append("\\begin{document}\n");
        sb.append("\\newlength{\\skrowlw}\n");
        // Capture the real linewidth from the exact nesting the skills block renders inside:
        // \begin{itemize}[leftmargin=0.15in, label={}] \item{ ... } -- a single level, unlike
        // the doubly-nested bullet items BulletLineMeasurer measures.
        sb.append("\\begin{itemize}[leftmargin=0.15in, label={}]\n\\item{\n");
        sb.append("\\global\\setlength{\\skrowlw}{\\linewidth}\n");
        sb.append("}\n\\end{itemize}\n");

        for (var e : rows.entrySet()) {
            String id = e.getKey();
            Row row = e.getValue();
            // The label is already valid LaTeX lifted verbatim from resume.tex's hardcoded
            // \skillrow calls (e.g. "Databases \& AI") -- escaping it again would mangle the
            // backslash. Only the item text, which comes from free-form profile input, needs it.
            String items = appRenderer.escapeRich(row.itemsJoined());
            sb.append("\\setbox0=\\hbox{\\small\\skillrowtext{").append(row.label()).append("}{")
              .append(items).append("}}\n");
            // Two layered TeX gotchas, confirmed against a real tectonic compile -- see the
            // matching comment in BulletLineMeasurer.buildTex for the full mechanism: \wd takes
            // an explicit register number, so a \space placed directly after \the\wd0 gets
            // consumed by the register-number scanner itself before \typeout ever sees it;
            // freezing it into \rpwidth via \edef first sidesteps that. Then \space is needed
            // after \rpwidth too, since any control word (this one included) swallows one
            // trailing literal space when the tokenizer reads it -- without it the log prints
            // "width=12.34ptlw=56.78pt" and MARKER below never matches a single row.
            sb.append("\\edef\\rpwidth{\\the\\wd0}\n");
            sb.append("\\typeout{RPSKILLROW id=").append(id)
              .append(" width=\\rpwidth\\space lw=\\the\\skrowlw}\n");
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
                double width = Double.parseDouble(m.group("width"));
                double lw = Double.parseDouble(m.group("lw"));
                out.put(m.group("id"), new Measured(width, lw));
            } catch (NumberFormatException ignored) {
                // A malformed marker leaves that candidate absent; the caller's size check
                // surfaces it as a fallback-to-existing-count case, never a crash.
            }
        }
        return out;
    }

    private static final Pattern MARKER = Pattern.compile(
            "RPSKILLROW id=(?<id>\\S+) width=(?<width>[-\\d.]+)pt lw=(?<lw>[-\\d.]+)pt");
}
