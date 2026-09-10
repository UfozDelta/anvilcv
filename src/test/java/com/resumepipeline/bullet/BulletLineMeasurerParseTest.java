package com.resumepipeline.bullet;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure logic only -- no tectonic, no Spring. Exercises the log-parsing regex and the
 * fill-ratio/decision math against a fabricated tectonic log, since the actual TeX box
 * arithmetic (buildTex's output) needs a real compile to verify and none is available here.
 */
class BulletLineMeasurerParseTest {

    private final BulletLineMeasurer measurer = new BulletLineMeasurer(null, null, null);

    @Test
    void parsesOneMarkerPerLine() {
        String log = """
                This is XeTeX, Version 3.14
                RPMEASURE id=abc-123 lines=1 width=80.5pt lw=468.0pt
                RPMEASURE id=def-456 lines=2 width=120.0pt lw=468.0pt
                Output written on in.pdf (1 page, 1234 bytes).
                """;

        Map<String, BulletLineMeasurer.Measured> out = measurer.parse(log);

        assertEquals(2, out.size());
        assertEquals(1, out.get("abc-123").lines());
        assertEquals(80.5 / 468.0, out.get("abc-123").lastLineFill(), 1e-6);
        assertEquals(2, out.get("def-456").lines());
    }

    @Test
    void emptyOrNullLogYieldsEmptyMap() {
        assertTrue(measurer.parse(null).isEmpty());
        assertTrue(measurer.parse("").isEmpty());
        assertTrue(measurer.parse("no markers here").isEmpty());
    }

    @Test
    void fillRatioClampedAtOneEvenIfWidthExceedsLinewidth() {
        // Shouldn't happen physically, but a measurement quirk must never produce a >100% fill.
        String log = "RPMEASURE id=x lines=1 width=500.0pt lw=468.0pt\n";
        Map<String, BulletLineMeasurer.Measured> out = measurer.parse(log);
        assertEquals(1.0, out.get("x").lastLineFill(), 1e-9);
    }

    @Test
    void isCleanFitRejectsThreePlusLines() {
        assertFalse(measurer.isCleanFit(new BulletLineMeasurer.Measured(3, 1.0)));
        assertFalse(measurer.isCleanFit(new BulletLineMeasurer.Measured(0, 1.0)));
    }

    @Test
    void isCleanFitRejectsASparseLastLine() {
        assertFalse(measurer.isCleanFit(new BulletLineMeasurer.Measured(2, 0.2)));
        assertTrue(measurer.isCleanFit(new BulletLineMeasurer.Measured(2, 0.9)));
        assertTrue(measurer.isCleanFit(new BulletLineMeasurer.Measured(1, 0.6)));
    }

    @Test
    void isCleanFitRejectsNull() {
        assertFalse(measurer.isCleanFit(null));
    }
}
