package com.resumepipeline.github;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SourceTracerTest {

    private static final RepoExplorer.Evidence POOL = new RepoExplorer.Evidence("scaleImpact", "Hikari pool tuned for Neon",
            "src/main/resources/application.yml", 40, 43, null,
            "hikari:\n  maximum-pool-size: 5\n  max-lifetime: 240000\n  connection-timeout: 30000");
    private static final RepoExplorer.Evidence JOBS = new RepoExplorer.Evidence("technicalDecisions", "in-memory async job progress",
            "src/main/java/com/resumepipeline/api/JobProgressStore.java", 16, 30, null,
            "public class JobProgressStore {\n    private final ConcurrentHashMap<UUID, JobState> store");
    private static final RepoExplorer.Evidence COMMIT = new RepoExplorer.Evidence("ownership", "fixed pool exhaustion",
            null, 0, 0, "0123456789abcdef", "0123456789 me 2026-01-01 fix: Neon pool exhaustion under 240000ms lifetime");

    @Test
    void quantityAndWordsPointAtTheRightSpans() {
        String bullet = "Tuned the **Hikari** connection pool for Neon scale-to-zero with a **240,000ms** max lifetime, cutting cold-start failures.";

        List<SourceTracer.Source> s = SourceTracer.trace(bullet, List.of(JOBS, POOL, COMMIT));

        assertEquals(2, s.size());
        assertEquals("src/main/resources/application.yml", s.get(0).path()); // strongest: number + hikari + pool
        assertEquals(40, s.get(0).startLine());
        assertEquals("0123456789abcdef", s.get(1).commit());
    }

    @Test
    void camelCaseIdentifiersMatchPlainEnglish() {
        String bullet = "Tracked async job progress in a concurrent in-memory store polled by the browser every 1.5s.";
        List<SourceTracer.Source> s = SourceTracer.trace(bullet, List.of(POOL, JOBS));
        assertEquals(1, s.size());
        assertTrue(s.get(0).path().endsWith("JobProgressStore.java"));
    }

    @Test
    void unrelatedBulletTracesToNothing() {
        assertTrue(SourceTracer.trace("Designed a Figma component library for the marketing site.", List.of(POOL, JOBS, COMMIT)).isEmpty());
        assertTrue(SourceTracer.trace("anything", List.of()).isEmpty());
    }
}
