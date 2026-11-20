package com.resumepipeline.application;

import com.resumepipeline.bullet.Bullet;
import com.resumepipeline.project.Project;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The JD coverage matrix: item extraction, scoring, angles and entry ordering. */
class JdCoverageTest {

    private static final String JD = """
            Fullstack engineer. Must be grad 2027 or later. Available Jan to Apr 2027.
            Required: React, Node.js and PostgreSQL.
            Preferred: Docker and Redis.
            Build and automate APIs; collaborate with design.
            """;

    @Test
    void extractsHardGatesMinimumPreferredAndVerbs() {
        JdCoverage.Matrix m = JdCoverage.items(JD, List.of("K8s"));
        assertFalse(m.byKind(JdCoverage.Kind.HARD_GATE).isEmpty());
        assertEquals(1, m.byKind(JdCoverage.Kind.MINIMUM).size());
        assertEquals(1, m.byKind(JdCoverage.Kind.PREFERRED).size());
        assertTrue(m.byKind(JdCoverage.Kind.RESPONSIBILITY).contains("build"));
        assertTrue(m.byKind(JdCoverage.Kind.RESPONSIBILITY).contains("automate"));
        assertTrue(m.byKind(JdCoverage.Kind.RESPONSIBILITY).contains("collaborat"));
        assertEquals(List.of("K8s"), m.byKind(JdCoverage.Kind.KEYWORD));
    }

    @Test
    void scoresHardGatesAndReportsMisses() {
        String rendered = "Fullstack engineer with a BSc, grad 2026. Built APIs with React and Node.js.";
        JdCoverage.Matrix m = JdCoverage.of(JD, rendered, List.of());
        assertTrue(m.hardGateMisses().stream().anyMatch(s -> s.contains("2027")),
                "a 2026 graduation does not satisfy a 2027 gate: " + m.hardGateMisses());

        JdCoverage.Matrix satisfied = JdCoverage.of(JD, rendered.replace("2026", "2027"), List.of());
        assertFalse(satisfied.hardGateMisses().stream().anyMatch(s -> s.contains("grad 2027")));
    }

    @Test
    void aliasAwareKeywordFallbackAndCoverageGaps() {
        String rendered = "Built APIs with Kubernetes and PostgreSQL; grad 2027.";
        JdCoverage.Matrix m = JdCoverage.of(JD, rendered, List.of("K8s", "Postgres"));
        JdCoverage.Item k8s = m.items().stream().filter(i -> i.text().equals("K8s")).findFirst().orElseThrow();
        assertEquals(JdCoverage.Status.COVERED, k8s.status(), "K8s must cover Kubernetes via KeywordScorer");
        assertTrue(m.coverageGaps().stream().anyMatch(s -> s.contains("Redis")),
                "preferred Redis is not on the page: " + m.coverageGaps());
        assertFalse(m.coverageGaps().stream().anyMatch(s -> s.startsWith("hard gate")));
    }

    @Test
    void bulletScoreLabelsCoveredThinAndMissing() {
        JdCoverage.Matrix m = JdCoverage.of(JD, "", List.of("React", "Rust"));
        assertEquals(JdCoverage.Status.COVERED, m.score("Built React views.").get(m.items().stream()
                .filter(i -> i.text().equals("React")).findFirst().orElseThrow()));
        var rust = m.items().stream().filter(i -> i.text().equals("Rust")).findFirst().orElseThrow();
        assertEquals(JdCoverage.Status.MISSING, m.score("Built React views.").get(rust));
    }

    @Test
    void anglesAndThreeAngleCheck() {
        assertEquals(JdCoverage.Angle.WHAT, JdCoverage.angleOf("Built a tool-calling agent for repo search."));
        assertEquals(JdCoverage.Angle.MECHANISM,
                JdCoverage.angleOf("Ran the agent under a 40-step budget using a deterministic repo map."));
        assertEquals(JdCoverage.Angle.OUTCOME,
                JdCoverage.angleOf("Cut p95 latency by 40% with a hash-locked grader that fails closed."));
        assertFalse(JdCoverage.hasThreeAngles(List.of(bullet(UUID.randomUUID(), "Built a thing."),
                bullet(UUID.randomUUID(), "Built another thing."), bullet(UUID.randomUUID(), "Built a third thing."))));
    }

    @Test
    void entriesAreOrderedByJdDensityAndAngles() {
        UUID denseId = UUID.randomUUID(), thinId = UUID.randomUUID();
        Project dense = new Project(UUID.randomUUID(), Project.Kind.PROJECT, "Dense", "d", null, null, null, null, null);
        Project thin = new Project(UUID.randomUUID(), Project.Kind.PROJECT, "Thin", "d", null, null, null, null, null);
        List<Bullet> selected = List.of(
                bullet(thinId, "Built a viewer."),
                bullet(denseId, "Built React views."),
                bullet(denseId, "Ran it through a deterministic pipeline using Postgres."),
                bullet(thinId, "Wrote docs."));
        JdCoverage.Matrix m = JdCoverage.of("", "", List.of("React", "PostgreSQL"));

        List<Bullet> ordered = JdCoverage.orderByDensityAndAngle(selected, m, Map.of(denseId, dense, thinId, thin));
        assertEquals(List.of(denseId, denseId, thinId, thinId),
                ordered.stream().map(Bullet::getProjectId).toList());
        assertEquals(List.of(JdCoverage.Angle.WHAT, JdCoverage.Angle.MECHANISM),
                ordered.subList(0, 2).stream().map(b -> JdCoverage.angleOf(b.getText())).toList());
    }

    private static Bullet bullet(UUID projectId, String text) {
        Bullet b = new Bullet(projectId, text, new String[0], "backend");
        try {
            var id = Bullet.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(b, UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return b;
    }
}
