package com.resumepipeline.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LatexEscaperTest {

    private final LatexEscaper esc = new LatexEscaper();
    private static final String NBSP = " ";

    @Test void plainText()  { assertEquals("hello world", esc.escape("hello world")); }
    @Test void nullInput()  { assertEquals("", esc.escape(null)); }

    @Test void percent()    { assertEquals("50\\%",  esc.escape("50%")); }
    @Test void ampersand()  { assertEquals("R\\&D",  esc.escape("R&D")); }
    @Test void underscore() { assertEquals("user\\_id", esc.escape("user_id")); }
    @Test void hash()       { assertEquals("\\#1",   esc.escape("#1")); }
    @Test void dollar()     { assertEquals("\\$5",   esc.escape("$5")); }
    @Test void braces()     { assertEquals("\\{x\\}", esc.escape("{x}")); }
    @Test void tilde()      { assertEquals("a\\textasciitilde{}b", esc.escape("a~b")); }
    @Test void caret()      { assertEquals("x\\textasciicircum{}2", esc.escape("x^2")); }

    @Test void backslash() {
        assertEquals("path\\textbackslash{}to", esc.escape("path\\to"));
    }

    @Test void backslashDoesNotCascade() {
        assertEquals("a\\textbackslash{}\\&b", esc.escape("a\\&b"));
    }

    @Test void allSpecialsTogether() {
        assertEquals(
            "\\% \\& \\_ \\# \\$ \\textasciitilde{} \\textasciicircum{} \\textbackslash{} \\{ \\}",
            esc.escape("% & _ # $ ~ ^ \\ { }")
        );
    }

    @Test void url() {
        assertEquals(
            "https://x.com/path?a=1\\&b=2\\#frag",
            esc.escape("https://x.com/path?a=1&b=2#frag")
        );
    }

    @Test void unicodeQuotes() {
        assertEquals("``hello''", esc.escape("“hello”"));
    }

    @Test void unicodeSingleQuotes() {
        // U+2018 always becomes a plain apostrophe (never a backtick) — see comment in
        // LatexEscaper: the LLM emits U+2018 on both ends of a span, so treating it as an
        // opening quote would render a wrong-facing backtick on the closing side.
        assertEquals("it's 'quoted'", esc.escape("it‘s ‘quoted‘"));
    }

    @Test void curlySingleQuoteBothEndsNoBacktick() {
        // LLM emits U+2018...U+2018 around code identifiers instead of U+2018...U+2019.
        assertEquals("'Shapiro-Wilk'", esc.escape("‘Shapiro-Wilk‘"));
        assertEquals("'KS'", esc.escape("‘KS‘"));
        assertEquals("'asyncio'", esc.escape("‘asyncio‘"));
    }

    @Test void properlyPairedSingleQuotesAlsoMatch() {
        // A correctly paired U+2018...U+2019 span still renders as matching apostrophes
        // (giving up the distinct opening-quote glyph in exchange for consistency).
        assertEquals("'quoted'", esc.escape("‘quoted’"));
    }

    @Test void possessiveApostropheSurvives() {
        assertEquals("4 venues' live rosters", esc.escape("4 venues’ live rosters"));
    }

    @Test void doubleCurlyQuotesUnaffected() {
        assertEquals("``hello''", esc.escape("“hello”"));
    }

    @Test void emDash()     { assertEquals("a---b", esc.escape("a—b")); }
    @Test void enDash()     { assertEquals("2024--2026", esc.escape("2024–2026")); }
    @Test void ellipsis()   { assertEquals("wait\\ldots{}", esc.escape("wait…")); }

    @Test void nbspBecomesTilde() {
        assertEquals("Mr.~Smith", esc.escape("Mr." + NBSP + "Smith"));
    }

    @Test void regularSpaceUnchanged() {
        assertEquals("a b c", esc.escape("a b c"));
    }
}
