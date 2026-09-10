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
import static org.junit.jupiter.api.Assertions.assertNull;

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

    /**
     * Pinned behavior, not a bug in {@link BulletLineMeasurer} itself: TeX's {@code \write}
     * family (which {@code \typeout} uses) DOUBLES a literal {@code #} for round-trip safety --
     * {@code id=backend#0} becomes {@code id=backend##0} in the compiled log. This class is
     * delimiter-agnostic (it just echoes back whatever id string the caller hands it), so the
     * hazard lives entirely in choosing that id -- {@code BulletService.recordMeasureDiagnostics}
     * originally used {@code category + "#" + index} and every lookup silently missed as a
     * result, despite the compile and the id regex both succeeding. It now uses {@code "_"}.
     * This test exists so a future id scheme reintroducing {@code #} fails loudly instead of
     * silently returning nothing measured.
     */
    @Test
    void aHashInTheIdIsDoubledByTexAndMustNeverBeUsedAsADelimiter() {
        assumeTrue(tectonicAvailable(), "tectonic not on PATH -- skipping real-compile check");

        PdfCompiler compiler = new PdfCompiler("tectonic", 30);
        LatexEscaper escaper = new LatexEscaper();
        LatexRenderer latexRenderer = new LatexRenderer(escaper);
        ApplicationRenderer appRenderer = new ApplicationRenderer(latexRenderer, escaper, null);
        BulletLineMeasurer measurer = new BulletLineMeasurer(latexRenderer, appRenderer, compiler);

        Map<String, BulletLineMeasurer.Measured> result = measurer.measure(
                Map.of("backend#0", "Added rate limiting."));

        // The compile and the id regex both succeed -- a row IS parsed -- just keyed under the
        // doubled id TeX actually wrote ("backend##0"), not the one the caller asked about. A
        // caller doing exactly what BulletService did, map.get("backend#0"), gets null: this is
        // the real, silent failure mode, not an empty map.
        assertNull(result.get("backend#0"),
                "a '#'-delimited id must NOT round-trip a lookup by the original id -- if this "
                        + "ever starts passing, TeX's \\write doubling behavior has changed and "
                        + "every id scheme built around avoiding '#' should be revisited");
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
