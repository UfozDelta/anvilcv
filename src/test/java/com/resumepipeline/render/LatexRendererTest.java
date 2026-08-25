package com.resumepipeline.render;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LatexRendererTest {

    private static final String TEMPLATE = "template/test-render.tex";

    private final LatexRenderer renderer = new LatexRenderer(new LatexEscaper());

    @Test void renderReplacesAllOccurrences() {
        String out = renderer.render(TEMPLATE, Map.of("NAME", "Ada", "BIO", "coder"));
        assertEquals("Name: Ada\nBio: coder\nRepeat: Ada\n", out);
    }

    @Test void renderEscapesValues() {
        String out = renderer.render(TEMPLATE, Map.of("NAME", "R&D", "BIO", "50%"));
        assertTrue(out.contains("Name: R\\&D"), out);
        assertTrue(out.contains("Bio: 50\\%"), out);
    }

    // A blank line inside a \textbf{...} argument becomes \par and aborts the LaTeX compile
    // with "Paragraph ended before \text@command was complete", so escaping must flatten it.
    @Test void escapeCollapsesNewlines() {
        assertEquals("a b", new LatexEscaper().escape("a\n\nb"));
        assertEquals("a b", new LatexEscaper().escape("  a\r\n\tb  "));
    }

    @Test void renderRawDoesNotEscape() {
        String out = renderer.renderRaw(TEMPLATE, Map.of("NAME", "R&D", "BIO", "\\textbf{x}"));
        assertTrue(out.contains("Name: R&D"), out);
        assertTrue(out.contains("Bio: \\textbf{x}"), out);
    }

    @Test void renderRawNullValueBecomesEmpty() {
        Map<String, String> values = new HashMap<>();
        values.put("NAME", null);
        values.put("BIO", "x");
        String out = renderer.renderRaw(TEMPLATE, values);
        assertTrue(out.contains("Name: \n"), out);
    }

    @Test void unreplacedTokensRemain() {
        String out = renderer.render(TEMPLATE, Map.of("NAME", "Ada"));
        assertTrue(out.contains("Bio: {{BIO}}"), out);
    }

    @Test void missingTemplateThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> renderer.render("template/does-not-exist.tex", Map.of()));
    }

    // An itemize with no \item aborts the LaTeX compile, so an empty section has to be
    // removed outright rather than rendered with an empty body.

    private static final String RESUME = "template/resume.tex";

    private Map<String, String> resumeValues(String education, String experience, String projects) {
        Map<String, String> v = new HashMap<>();
        v.put("EDUCATION_ITEMS", education);
        v.put("EXPERIENCE_ITEMS", experience);
        v.put("PROJECT_ITEMS", projects);
        return v;
    }

    @Test void emptySectionIsRemovedEntirely() {
        String out = renderer.renderRaw(RESUME, resumeValues("\\item{edu}", "\\item{exp}", ""));
        assertFalse(out.contains("\\section{Projects}"), out);
        assertTrue(out.contains("\\section{Education}"), out);
        assertTrue(out.contains("\\section{Experience}"), out);
    }

    @Test void blankSectionCountsAsEmpty() {
        String out = renderer.renderRaw(RESUME, resumeValues("\\item{edu}", "   \n  ", "\\item{proj}"));
        assertFalse(out.contains("\\section{Experience}"), out);
    }

    @Test void allSectionsEmptyLeavesNoOrphanItemize() {
        String out = renderer.renderRaw(RESUME, resumeValues("", "", ""));
        assertFalse(out.contains("\\section{Education}"), out);
        assertFalse(out.contains("\\section{Experience}"), out);
        assertFalse(out.contains("\\section{Projects}"), out);
        // the \newcommand definitions in the preamble stay; only the invocations go
        assertEquals(1, count(out, "\\resumeSubHeadingListStart"), out);
        assertEquals(1, count(out, "\\resumeSubHeadingListEnd"), out);
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) n++;
        return n;
    }

    @Test void populatedSectionKeepsItsContent() {
        String out = renderer.renderRaw(RESUME, resumeValues("\\item{edu}", "\\item{exp}", "\\item{proj}"));
        assertTrue(out.contains("\\section{Projects}"), out);
        assertTrue(out.contains("\\item{proj}"), out);
    }

    @Test void markersNeverSurviveIntoOutput() {
        String out = renderer.renderRaw(RESUME, resumeValues("\\item{edu}", "", "\\item{proj}"));
        assertFalse(out.contains("%%SECTION:"), out);
        assertFalse(out.contains("%%ENDSECTION%%"), out);
    }
}
