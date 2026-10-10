package com.resumepipeline.field;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FieldTest {

    @Test void slugsRoundTrip() {
        for (Field f : Field.values()) assertEquals(f, Field.of(f.slug()));
    }

    @Test void unknownOrBlankIsSoftware() {
        assertEquals(Field.SOFTWARE, Field.of(null));
        assertEquals(Field.SOFTWARE, Field.of(""));
        assertEquals(Field.SOFTWARE, Field.of("astrology"));
    }

    @Test void parsingIgnoresCaseAndSpaces() {
        assertEquals(Field.MECHANICAL, Field.of("  Mechanical "));
    }

    @Test void knownIsStrict() {
        assertTrue(Field.isKnown("other"));
        assertFalse(Field.isKnown("astrology"));
        assertFalse(Field.isKnown(null));
    }

    // "Other / not sure" stores the choice but claims nothing: it runs the software preset.
    @Test void otherRunsTheSoftwarePreset() {
        assertSame(Field.SOFTWARE.preset(), Field.OTHER.preset());
    }

    @Test void softwarePresetKeepsTheShippedWording() {
        FieldPreset p = Field.SOFTWARE.preset();
        assertEquals(java.util.List.of("ai-ml", "backend", "data", "general"), p.lensSlugs());
        assertEquals(java.util.List.of("Languages", "Frameworks", "Databases & AI", "DevOps & Tools", "AI & Integrations"),
                p.skillLabels());
    }

    @Test void everyPresetFillsTheFiveSkillRowsAndEndsInGeneral() {
        for (FieldPreset p : FieldPresets.all()) {
            assertEquals(5, p.skillLabels().size(), p.name());
            assertEquals("general", p.lensSlugs().get(p.lensSlugs().size() - 1), p.name());
            assertEquals(p.lensSlugs().size(), p.lensSlugs().stream().distinct().count(), p.name());
        }
    }
}
