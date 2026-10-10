package com.resumepipeline.application;

import com.resumepipeline.field.Field;
import com.resumepipeline.profile.Profile;
import com.resumepipeline.profile.ProfileService;
import com.resumepipeline.render.LatexEscaper;
import com.resumepipeline.render.LatexRenderer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** MULTI_FIELD_PLAN step 4: the Technical Skills rows are labelled for the user's field. */
class ApplicationRendererFieldTest {

    private static String render(Field field) {
        UUID user = UUID.randomUUID();
        Profile p = new Profile();
        p.setUserId(user);
        p.setName("Ada Lovelace");
        p.setSkillsLanguages("SolidWorks, CATIA");
        p.setSkillsFrameworks("ANSYS");
        p.setSkillsDatabases("CNC, GD&T");
        p.setSkillsDevops("LabVIEW");
        p.setSkillsInterests("ASME Y14.5");
        p.setField(field);
        ProfileService profiles = mock(ProfileService.class);
        when(profiles.get(user)).thenReturn(p);
        ApplicationRenderer renderer = new ApplicationRenderer(new LatexRenderer(new LatexEscaper()), new LatexEscaper(), profiles);
        return renderer.render(user, List.of(), Map.of(), List.of(), Map.of(), List.of());
    }

    // The software labels are the ones the template hard-coded before fields: the PDF must not change.
    @Test void softwareKeepsTheShippedLabels() {
        String tex = render(Field.SOFTWARE);
        assertTrue(tex.contains("\\skillrow{Languages}{"), tex);
        assertTrue(tex.contains("\\skillrow{Frameworks}{"));
        assertTrue(tex.contains("\\skillrow{Databases \\& AI}{"));
        assertTrue(tex.contains("\\skillrow{DevOps \\& Tools}{"));
        assertTrue(tex.contains("\\skillrow{AI \\& Integrations}{"));
    }

    @Test void otherPrintsTheSoftwareLabels() {
        assertTrue(render(Field.OTHER).contains("\\skillrow{Databases \\& AI}{"));
    }

    @Test void mechanicalPrintsItsOwnLabelsAndEscapesTheAmpersand() {
        String tex = render(Field.MECHANICAL);
        assertTrue(tex.contains("\\skillrow{CAD \\& Modeling}{"), tex);
        assertTrue(tex.contains("\\skillrow{Simulation \\& Analysis}{"));
        assertTrue(tex.contains("\\skillrow{Manufacturing \\& Quality}{"));
        assertTrue(tex.contains("\\skillrow{Lab \\& Tools}{"));
        assertTrue(tex.contains("\\skillrow{Standards \\& Interests}{"));
        assertFalse(tex.contains("\\skillrow{Languages}"));
        assertFalse(tex.contains("SKILL_LABEL"), "no label token may survive into the TeX");
    }
}
