package com.resumepipeline.llm;

import com.resumepipeline.progress.ProgressLog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The recruiter pass is the one call whose output names specific bullets, so a hallucinated
 * id would attach criticism to a bullet the user cannot see. These cover the sanitising.
 */
class BaseLlmClientRecruiterTest {

    private static final LlmClient.RenderedBullet A =
            new LlmClient.RenderedBullet("a", "shipped a thing", "P");
    private static final LlmClient.RenderedBullet B =
            new LlmClient.RenderedBullet("b", "shipped another thing", "P");

    private static BaseLlmClient.RecruiterEnvelope envelope() {
        BaseLlmClient.RecruiterEnvelope env = new BaseLlmClient.RecruiterEnvelope();
        env.evidenceStrength = 80;
        env.relevanceDensity = 70;
        env.weakestBulletId = "a";
        env.thinnestRequirement = "Kubernetes at scale";
        env.weaknesses = List.of("no metrics", "no ownership");
        env.bulletVerdicts = List.of(verdict("a", "weak", "vague"), verdict("b", "keep", "solid"));
        return env;
    }

    private static BaseLlmClient.RecruiterVerdictJson verdict(String id, String v, String reason) {
        BaseLlmClient.RecruiterVerdictJson j = new BaseLlmClient.RecruiterVerdictJson();
        j.bulletId = id;
        j.verdict = v;
        j.reason = reason;
        return j;
    }

    private static LlmClient.RecruiterResult run(BaseLlmClient.RecruiterEnvelope env) {
        return BaseLlmClient.postProcessRecruiter(env, List.of(A, B), ProgressLog.noOp());
    }

    @Test
    void verdictBandBoundaries() {
        assertEquals("Sharp", BaseLlmClient.recruiterVerdictFor(100));
        assertEquals("Sharp", BaseLlmClient.recruiterVerdictFor(75));
        assertEquals("Solid", BaseLlmClient.recruiterVerdictFor(74));
        assertEquals("Solid", BaseLlmClient.recruiterVerdictFor(60));
        assertEquals("Serviceable", BaseLlmClient.recruiterVerdictFor(59));
        assertEquals("Serviceable", BaseLlmClient.recruiterVerdictFor(45));
        assertEquals("Unfocused", BaseLlmClient.recruiterVerdictFor(44));
        assertEquals("Unfocused", BaseLlmClient.recruiterVerdictFor(30));
        assertEquals("Weak", BaseLlmClient.recruiterVerdictFor(29));
        assertEquals("Weak", BaseLlmClient.recruiterVerdictFor(0));
    }

    @Test
    void bandVocabularyIsDistinctFromTheFitScore() {
        // Two badges sit side by side in the UI; sharing words makes them indistinguishable.
        for (int i = 0; i <= 100; i++) {
            assertTrue(!BaseLlmClient.recruiterVerdictFor(i).contains("Fit"),
                    "recruiter band reused fit vocabulary at " + i);
        }
    }

    @Test
    void scoresAreClampedAndOverallIsComputedInJava() {
        BaseLlmClient.RecruiterEnvelope env = envelope();
        env.evidenceStrength = 140;
        env.relevanceDensity = -20;
        LlmClient.RecruiterResult r = run(env);
        assertEquals(100, r.evidenceStrength());
        assertEquals(0, r.relevanceDensity());
        assertEquals(50, r.overall());
        assertEquals("Serviceable", r.verdict());
    }

    @Test
    void hallucinatedBulletIdIsDropped() {
        BaseLlmClient.RecruiterEnvelope env = envelope();
        env.bulletVerdicts = List.of(verdict("a", "keep", "ok"), verdict("ghost", "drop", "not real"));
        LlmClient.RecruiterResult r = run(env);
        assertEquals(1, r.bulletVerdicts().size());
        assertEquals("a", r.bulletVerdicts().get(0).bulletId());
    }

    @Test
    void invalidVerdictStringIsDroppedAndCasingNormalised() {
        BaseLlmClient.RecruiterEnvelope env = envelope();
        env.bulletVerdicts = List.of(verdict("a", "KEEP", "ok"), verdict("b", "excellent", "?"));
        LlmClient.RecruiterResult r = run(env);
        assertEquals(1, r.bulletVerdicts().size());
        assertEquals("keep", r.bulletVerdicts().get(0).verdict());
    }

    @Test
    void outOfSetWeakestBulletIdIsNulled() {
        BaseLlmClient.RecruiterEnvelope env = envelope();
        env.weakestBulletId = "ghost";
        assertNull(run(env).weakestBulletId());

        env.weakestBulletId = null;
        assertNull(run(env).weakestBulletId());

        env.weakestBulletId = "b";
        assertEquals("b", run(env).weakestBulletId());
    }
}
