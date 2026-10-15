package com.resumepipeline.project;

import com.resumepipeline.api.dto.ResumeDtos.ParseResumeResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parsed description is the source every generated bullet is written (and number-checked)
 * against, so a truncated bullet or a phantom entry here degrades the whole bank.
 */
class ResumeParserServiceTest {

    private final ResumeParserService parser = new ResumeParserService();

    private static final String WRAPPED_HEAD =
            "• Built a real-time ingestion pipeline over Kafka and Flink for the pricing team using";

    @Test
    void wrappedBulletTailJoinsItsBulletInsteadOfOpeningAnEntry() {
        ParseResumeResponse r = parser.parse(String.join("\n",
                "EXPERIENCE",
                "Software Engineer   Jan 2024 - Present",
                "Acme Corp   Toronto, ON",
                WRAPPED_HEAD,
                "AWS Lambda and Redis, serving 2000 users",
                "• Shipped the billing service.",
                "Data Analyst   May 2022 - Dec 2023",
                "Beta Inc   Remote",
                "• Automated weekly reporting."));

        assertEquals(2, r.experiences().size());
        assertEquals(WRAPPED_HEAD.substring(2) + " AWS Lambda and Redis, serving 2000 users\nShipped the billing service.",
                r.experiences().get(0).description());
        assertEquals("Data Analyst", r.experiences().get(1).title());
    }

    @Test
    void shortOrFinishedBulletDoesNotSwallowTheNextProjectHeader() {
        ParseResumeResponse r = parser.parse(String.join("\n",
                "PROJECTS",
                "AnvilCV -- Java, React",
                "• Tailors resumes",
                "iOS Notes App",
                "• Offline-first note sync with a long enough line that it would wrap on the page.",
                "eBay Clone",
                "• Bids."));

        assertEquals(3, r.projects().size());
        assertEquals("iOS Notes App", r.projects().get(1).name());
        assertEquals("eBay Clone", r.projects().get(2).name());
    }

    @Test
    void wordSymbolBulletsAndColonHeadersAreRecognised() {
        ParseResumeResponse r = parser.parse(String.join("\n",
                "Work Experience:",
                "Intern   Jun 2023 - Aug 2023",
                "Gamma LLC   NYC",
                " Migrated CI to GitHub Actions.",
                "● Cut build time.",
                "•",
                "Wrote the runbook."));

        assertEquals(1, r.experiences().size());
        assertEquals("Migrated CI to GitHub Actions.\nCut build time.\nWrote the runbook.",
                r.experiences().get(0).description());
    }

    @Test
    void projectTechStackIsKeptInTheDescription() {
        ParseResumeResponse r = parser.parse(String.join("\n",
                "PROJECTS",
                "AnvilCV -- Spring Boot, React   2025 - Present",
                "• Ranks a bullet bank against job descriptions."));

        assertEquals("AnvilCV", r.projects().get(0).name());
        assertTrue(r.projects().get(0).description().endsWith("Tech stack: Spring Boot, React"),
                r.projects().get(0).description());
    }

    @Test
    void repeatedSectionHeaderDoesNotEraseTheFirstBlock() {
        ParseResumeResponse r = parser.parse(String.join("\n",
                "EXPERIENCE",
                "Engineer   Jan 2024 - Present",
                "Acme   Remote",
                "• Shipped it.",
                "PROJECTS",
                "Thing",
                "• Did it.",
                "EXPERIENCE",
                "Analyst   Jan 2020 - Dec 2021",
                "Beta   Remote",
                "• Reported it."));

        assertEquals(2, r.experiences().size());
    }

    @Test
    void bulletsComeBackOnePerLineAndNeverIncludeTheTechStackLine() {
        ParseResumeResponse r = parser.parse(String.join("\n",
                "EXPERIENCE",
                "Engineer   Jan 2024 - Present",
                "Acme   Remote",
                "• Shipped the billing service.",
                "• Cut p95 latency from 1.8s to 240ms.",
                "PROJECTS",
                "AnvilCV -- Spring Boot, React   2025 - Present",
                "• Ranks a bullet bank against job descriptions."));

        assertEquals(java.util.List.of("Shipped the billing service.", "Cut p95 latency from 1.8s to 240ms."),
                r.experiences().get(0).bullets());
        assertEquals(java.util.List.of("Ranks a bullet bank against job descriptions."), r.projects().get(0).bullets());
    }

    @Test
    void textWithoutBulletGlyphsStillYieldsEntriesAndBullets() {
        ParseResumeResponse r = parser.parse(String.join("\n",
                "EXPERIENCE",
                "Backend Engineering Intern   Jun 2025 - Aug 2025",
                "Northwind Robotics   Austin, TX",
                "Built a Kafka-fed telemetry ingestion service sustaining 4,000 messages per second",
                "Reworked Postgres indexes behind the fleet dashboard, cutting p95 latency by 87 percent",
                "Data Analyst   May 2022 - Dec 2023",
                "Beta Inc   Remote",
                "Automated the weekly reporting pipeline and removed four hours of manual work each week"));

        assertEquals(2, r.experiences().size());
        assertEquals("Backend Engineering Intern", r.experiences().get(0).title());
        assertEquals(2, r.experiences().get(0).bullets().size());
        assertEquals("Data Analyst", r.experiences().get(1).title());
        assertEquals(1, r.experiences().get(1).bullets().size());
    }

    @Test
    void glyphlessLowercaseTailJoinsItsBulletInsteadOfBecomingOne() {
        ParseResumeResponse r = parser.parse(String.join("\n",
                "EXPERIENCE",
                "Engineer   Jan 2024 - Present",
                "Acme   Remote",
                "Built a real-time ingestion pipeline over Kafka and Flink for the pricing team using",
                "and AWS Lambda with Redis, serving 2000 users",
                "Shipped the billing service for every customer region in the first quarter."));

        assertEquals(1, r.experiences().size());
        assertEquals(2, r.experiences().get(0).bullets().size(), r.experiences().get(0).bullets().toString());
    }
}
