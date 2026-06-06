package com.resumepipeline.render;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
