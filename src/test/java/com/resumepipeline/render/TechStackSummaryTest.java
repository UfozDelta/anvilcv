package com.resumepipeline.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TechStackSummaryTest {

    // Both of these shipped into a real PDF and ran off the right page edge.
    @Test void dropsVersionsAndParentheticalDetail() {
        assertEquals("Java, Spring Boot, PostgreSQL, Flyway", TechStackSummary.shorten(
                "Java 21, Spring Boot 3.4.0 (web, data-jpa, security, validation), "
                + "PostgreSQL (Neon, prod) with Flyway migrations and Testcontainers"));
    }

    @Test void dropsQuotedCodeSpans() {
        assertEquals("Python, pandas", TechStackSummary.shorten(
                "Python 3.10 ('from __future__ import annotations', PEP 604 'str | None' unions), pandas 2"));
    }

    @Test void alreadyShortStackSurvivesIntact() {
        assertEquals("Java, React, Docker", TechStackSummary.shorten("Java, React, Docker"));
    }

    // Longest-first matching: "Spring Boot" must claim the text before "Spring" sees it.
    @Test void prefersTheLongerName() {
        assertEquals("Spring Boot", TechStackSummary.shorten("Spring Boot"));
    }

    // Unknown toolchain: better a short verbatim heading than an empty one.
    @Test void unmatchedStackFallsBackToShortFragments() {
        String out = TechStackSummary.shorten("Frobnicator 9, Widgetron mesh, some very long prose clause here");
        assertEquals("Frobnicator 9, Widgetron mesh", out);
    }

    @Test void nullAndBlankAreEmpty() {
        assertEquals("", TechStackSummary.shorten(null));
        assertEquals("", TechStackSummary.shorten("   "));
    }

    @Test void headingStaysShort() {
        String out = TechStackSummary.shorten(
                "Java 21, Spring Boot 3.4.0 (web), PostgreSQL, Redis, Kafka, Docker, Kubernetes, Terraform");
        assertTrue(out.length() <= 60, out);
    }
}
