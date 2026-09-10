package com.resumepipeline.application;

import com.resumepipeline.render.LatexEscaper;
import com.resumepipeline.render.LatexRenderer;
import com.resumepipeline.render.PdfCompiler;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Real-compile regression test for the skills-stretch bug: the original {@code \typeout} line
 * put {@code \the\wd0} directly against a following space and {@code lw=}, which TeX silently
 * swallows -- the register-number scanner for {@code \wd<n>} expands and consumes an inserted
 * {@code \space} while looking for more digits, and even after that a plain control word eats
 * a trailing literal space regardless of what it expands to. Net effect in production: {@link
 * SkillRowMeasurer#MARKER} never matched a single row, so every skill category silently stayed
 * at {@code BulletSelector.MIN_SKILLS_PER_CATEGORY} (6) no matter how much raw profile material
 * was available to stretch into -- the exact bug this test guards against regressing.
 *
 * <p>Skips cleanly when no {@code tectonic} binary is on PATH.
 */
class SkillRowMeasurerRealCompileTest {

    @Test
    void measuresARealSkillRowAgainstTectonic() {
        assumeTrue(tectonicAvailable(), "tectonic not on PATH -- skipping real-compile check");

        PdfCompiler compiler = new PdfCompiler("tectonic", 30);
        LatexEscaper escaper = new LatexEscaper();
        LatexRenderer latexRenderer = new LatexRenderer(escaper);
        ApplicationRenderer appRenderer = new ApplicationRenderer(latexRenderer, escaper, null);
        SkillRowMeasurer measurer = new SkillRowMeasurer(latexRenderer, appRenderer, compiler);

        Map<String, SkillRowMeasurer.Row> rows = Map.of(
                "languages_9", new SkillRowMeasurer.Row("Languages",
                        "Python, Java, TypeScript, JavaScript, SQL, Assembly, Bash, C++, R"));

        Map<String, SkillRowMeasurer.Measured> result = measurer.measure(rows);

        assertEquals(1, result.size(), "the row must produce a parsed measurement");
        SkillRowMeasurer.Measured m = result.get("languages_9");
        assertTrue(m.width() > 0, "measured width must be a real positive value, not left at 0");
        assertTrue(m.linewidth() > m.width(), "a 9-item language line must fit well inside the page width");
    }

    private static boolean tectonicAvailable() {
        try {
            Process p = new ProcessBuilder("tectonic", "--version").start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
