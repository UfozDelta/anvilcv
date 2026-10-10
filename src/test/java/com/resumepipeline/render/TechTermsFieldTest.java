package com.resumepipeline.render;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** MULTI_FIELD_PLAN step 4: a mechanical stack shortens to its tools, and a software stack is not disturbed. */
class TechTermsFieldTest {

    @Test void mechanicalToolsKeepTheirCanonicalNames() {
        assertEquals("SolidWorks, ANSYS, GD&T, CNC",
                TechStackSummary.shorten("solidworks 2023, Ansys Mechanical (static FEA), gd&t per ASME Y14.5, cnc"));
    }

    @Test void matchAllFindsEveryMechanicalTerm() {
        List<String> all = TechStackSummary.matchAll("SolidWorks, ANSYS, Abaqus, LabVIEW, DFMEA, CFD, Six Sigma");
        assertEquals(List.of("SolidWorks", "ANSYS", "Abaqus", "LabVIEW", "DFMEA", "CFD", "Six Sigma"),
                all.stream().sorted(java.util.Comparator.comparingInt(
                        t -> List.of("SolidWorks", "ANSYS", "Abaqus", "LabVIEW", "DFMEA", "CFD", "Six Sigma").indexOf(t))).toList());
    }

    @Test void aSoftwareStackIsUnchanged() {
        assertEquals("Java, Spring Boot, PostgreSQL, Docker",
                TechStackSummary.shorten("Java 21, Spring Boot 3.4, PostgreSQL, Docker, Redis"));
    }
}
