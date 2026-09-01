package com.resumepipeline.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The page count is read off tectonic's own log rather than by parsing the PDF, so the
 * parser is the only thing standing between us and a wrong number. A wrong count is worse
 * than none — every unrecognised shape must come back null.
 */
class PdfCompilerPageCountTest {

    private static final String REAL_LOG = """
            This is XeTeX, Version 3.141592653-2.6-0.999995 (TeX Live 2022)
            (in.tex
            LaTeX2e <2021-11-15>
            [1]
            Output written on in.pdf (1 page, 48512 bytes).
            """;

    @Test
    void parsesSinglePageFromTectonicLog() {
        assertEquals(1, PdfCompiler.Result.parsePageCount(REAL_LOG));
    }

    @Test
    void parsesOverflowToTwoPages() {
        assertEquals(2, PdfCompiler.Result.parsePageCount(
                "Output written on in.pdf (2 pages, 61204 bytes)."));
    }

    @Test
    void nullWhenLineAbsentOrUnparseable() {
        assertNull(PdfCompiler.Result.parsePageCount(null));
        assertNull(PdfCompiler.Result.parsePageCount(""));
        assertNull(PdfCompiler.Result.parsePageCount("note: rerunning to resolve references"));
        assertNull(PdfCompiler.Result.parsePageCount("Output written on in.pdf (some pages, 5 bytes)."));
    }

    @Test
    void successResultCarriesTheCountAndFailureCarriesNone() {
        assertEquals(1, PdfCompiler.Result.success(new byte[]{1}, REAL_LOG).pageCount());
        assertNull(PdfCompiler.Result.failure("exit 1", REAL_LOG).pageCount());
    }
}
