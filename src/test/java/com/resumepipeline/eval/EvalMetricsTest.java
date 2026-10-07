package com.resumepipeline.eval;

import com.resumepipeline.config.GenerationConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EvalMetricsTest {

    private static final UUID P = UUID.randomUUID();
    private static final UUID Q = UUID.randomUUID();
    private static final GenerationConfig CFG = new GenerationConfig();

    private static EvalItem item(UUID project, String category, String text) {
        return new EvalItem(project, "p", "PROJECT", category, "PENDING", List.of(), text);
    }

    /** ~85-char single-line bullet: inside the default one-line band. */
    private static String fit(String opener, String subject) {
        return opener + " a cache layer for the " + subject + " service in Java and Redis to cut DB load.";
    }

    @Test
    void vanityCatchesVolumeCountsButNotOutcomes() {
        assertTrue(EvalMetrics.VANITY.matcher("Shipped 120 commits across the repo.").find());
        assertTrue(EvalMetrics.VANITY.matcher("Wrote 6,062 lines of code in Go.").find());
        assertTrue(EvalMetrics.VANITY.matcher("Refactored 14 Java classes.").find());
        assertTrue(EvalMetrics.VANITY.matcher("Touched 40+ files.").find());
        assertFalse(EvalMetrics.VANITY.matcher("Cut p95 latency from 800ms to 120ms for 3 services.").find());
    }

    @Test
    void countsSentences() {
        assertEquals(1, EvalMetrics.sentences("Built an API in Java 17, e.g. for billing."));
        assertEquals(2, EvalMetrics.sentences("Built an API. This **rigorous** design paid off."));
        assertEquals(2, EvalMetrics.sentences("Built an API. **Cut** costs."));
    }

    @Test
    void openerIsFirstWordLowercasedWithoutBold() {
        assertEquals("architected", EvalMetrics.opener("**Architected** a pipeline."));
    }

    @Test
    void separatesNearIdenticalRepeatsFromIntendedOverlap() {
        String base = "Built a Redis cache layer for the pricing service in Java to cut database load";
        List<EvalItem> items = List.of(
                item(P, "backend", base + "."),
                item(P, "backend", base + " sharply."),             // near-identical prose
                item(P, "data", "Designed a Redis cache for the pricing service to reduce database load in Java."),
                item(Q, "backend", base + "."));                    // same text, other project: not a dup
        List<EvalMetrics.Flags> f = EvalMetrics.flag(items, Map.of(), CFG);
        assertTrue(f.get(0).identical());
        assertTrue(f.get(1).identical());
        assertFalse(f.get(2).identical());
        assertTrue(f.get(2).overlap());
        assertFalse(f.get(3).identical());
        assertFalse(f.get(3).overlap());
    }

    @Test
    void unbackedNumbersAreJudgedAgainstTheProjectSourceOnly() {
        List<EvalItem> items = List.of(
                item(P, "backend", "Cut p95 latency by 40% with a Redis cache in front of Postgres."),
                item(P, "backend", "Cut p95 latency by 90% with a Redis cache in front of Postgres."),
                item(Q, "backend", "Cut p95 latency by 90% with a Redis cache in front of Postgres."));
        EvalMetrics.Summary s = EvalMetrics.summarize(items, Map.of(P, "latency dropped 40%"), CFG);
        // Q has no source, so it is excluded rather than counted clean: 1 of 2 sourced.
        assertEquals(0.5, s.unbackedRate(), 1e-9);
        assertEquals(List.of("90%"), EvalMetrics.flag(items, Map.of(P, "40%"), CFG).get(1).unbacked());
    }

    @Test
    void summaryRatesAndOpeners() {
        List<EvalItem> items = List.of(
                item(P, "backend", fit("Built", "pricing")),
                item(P, "data", fit("Built", "billing")),
                item(Q, "backend", fit("Designed", "pricing")),
                item(Q, "backend", "Worked on stuff."));
        EvalMetrics.Summary s = EvalMetrics.summarize(items, Map.of(), CFG);
        assertEquals(4, s.count());
        assertEquals(2, s.projects());
        assertEquals(2.0, s.perProject(), 1e-9);
        assertEquals(0.75, s.lengthFitRate(), 1e-9);        // "Worked on stuff." is too short
        assertEquals(0.25, s.weakOpenerRate(), 1e-9);
        assertEquals(0.75, s.openerDiversity(), 1e-9);      // built, designed, worked / 4
        assertEquals("built x2", s.topOpeners().get(0));
        assertEquals(Map.of("backend", 3, "data", 1), s.categories());
    }

    @Test
    void emptySetIsAllZeros() {
        EvalMetrics.Summary s = EvalMetrics.summarize(List.of(), Map.of(), CFG);
        assertEquals(0, s.count());
        assertEquals(0, s.identicalRate());
    }
}
