package com.resumepipeline.application;

import com.resumepipeline.llm.BulletTextRules;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cover letter goes through the same no-fabrication check as the bullets, but with a
 * wider source context: the selected bullet texts plus the job description. These pin the
 * two halves of that context, since dropping either one silently changes what the letter
 * is allowed to say.
 */
class CoverLetterGuardrailTest {

    private static final String BULLETS =
            "Cut p99 latency by **40%** across the ingest path. Indexed 64,000 listings nightly.";
    private static final String JD =
            "We are a 500-person engineering org shipping to 12M monthly users.";

    /** Mirrors the source context ApplicationService builds. */
    private static List<String> check(String letter) {
        return BulletTextRules.fabricatedNumbers(letter, BULLETS + " " + JD);
    }

    @Test
    void acceptsFigureRestatedFromASelectedBullet() {
        assertTrue(check("I cut p99 latency by 40% on that team.").isEmpty());
    }

    @Test
    void acceptsEmployerFigureQuotedFromTheJd() {
        // The whole reason the JD is in the source context: citing the posting's own
        // numbers back at them is normal cover-letter writing, not fabrication.
        assertTrue(check("Joining a 500-person org serving 12M users is exactly the scale I want.").isEmpty());
    }

    @Test
    void flagsAnInflatedVersionOfARealMetric() {
        assertEquals(List.of("60%"), check("I cut p99 latency by 60% on that team."));
    }

    @Test
    void flagsAFigureFromNeitherSource() {
        assertEquals(List.of("$2M"), check("I owned a $2M budget."));
    }

    @Test
    void nullLetterIsClean() {
        assertTrue(BulletTextRules.fabricatedNumbers(null, BULLETS + " " + JD).isEmpty());
    }
}
