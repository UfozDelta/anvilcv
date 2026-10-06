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
}
