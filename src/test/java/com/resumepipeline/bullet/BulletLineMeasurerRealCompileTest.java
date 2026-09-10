package com.resumepipeline.bullet;

import com.resumepipeline.application.ApplicationRenderer;
import com.resumepipeline.render.LatexEscaper;
import com.resumepipeline.render.LatexRenderer;
import com.resumepipeline.render.PdfCompiler;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Real-compile regression test for the bug fixed in this change: the original {@code \typeout}
 * line put {@code \the\wd3} directly against a following space and {@code lw=}, which TeX
 * silently swallows (a control word -- and, one layer deeper, the register-number scanner for
 * {@code \wd<n>} itself -- eats a trailing space regardless of what follows), so {@link
 * BulletLineMeasurer#MARKER} never matched a single row in production. Skips cleanly when no
 * {@code tectonic} binary is on PATH, since most environments won't have one -- this exists to
 * catch a REGRESSION of the same bug, not to gate CI on an optional dependency.
 */
class BulletLineMeasurerRealCompileTest {

    @Test
    void measuresARealBulletAgainstTectonic() {
        assumeTrue(tectonicAvailable(), "tectonic not on PATH -- skipping real-compile check");

        PdfCompiler compiler = new PdfCompiler("tectonic", 30);
        LatexEscaper escaper = new LatexEscaper();
        LatexRenderer latexRenderer = new LatexRenderer(escaper);
        ApplicationRenderer appRenderer = new ApplicationRenderer(latexRenderer, escaper, null);
        BulletLineMeasurer measurer = new BulletLineMeasurer(latexRenderer, appRenderer, compiler);

        Map<String, BulletLineMeasurer.Measured> result = measurer.measure(Map.of(
                "one-liner", "Built a distributed ingestion service that cut nightly batch turnaround for the team.",
                "three-liner", "Engineered a distributed ingestion service that replaced the nightly batch job for the "
                        + "analytics team, adding backpressure, retry with jitter, and a dead-letter queue so a "
                        + "slow downstream consumer could no longer stall the whole pipeline or silently drop "
                        + "records during a partial outage window."));

        assertEquals(2, result.size(), "both bullets must produce a parsed measurement");
        assertEquals(1, result.get("one-liner").lines());
        assertFalse(result.get("three-liner").lines() < 3,
                "the long bullet must not be misreported as fitting in fewer lines");
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
