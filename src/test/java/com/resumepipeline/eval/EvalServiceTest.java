package com.resumepipeline.eval;

import com.resumepipeline.application.Application;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EvalServiceTest {

    @Test
    void baselineFileHoldsTheFrozenBank() {
        List<EvalItem> items = EvalService.readBaseline();
        assertEquals(196, items.size());
        assertEquals(9, items.stream().map(EvalItem::projectId).distinct().count());
        assertTrue(items.stream().noneMatch(i -> i.text().matches("(?s).*[\\w.+-]+@[\\w-]+\\.\\w+.*")),
                "no email addresses in the committed baseline");
    }

    @Test
    void jobMatchComparesKeywordCoverageOfThePreFilteredCandidates() {
        UUID user = UUID.randomUUID(), p = UUID.randomUUID();
        Application app = new Application();
        app.setUserId(user);
        app.setAtsMatched(new String[]{"Kubernetes"});
        app.setAtsMissing(new String[]{"Kafka"});
        app.setRoleEmphasis("backend+devops");
        List<EvalItem> a = List.of(new EvalItem(p, "P", "PROJECT", "backend", "PENDING", List.of(),
                "Deployed services on K8s."));
        List<EvalItem> b = List.of(new EvalItem(p, "P", "PROJECT", "backend", "PENDING", List.of(),
                "Deployed services on K8s fed by Kafka streams."));

        Map<String, Object> out = EvalService.jobMatch(List.of(app), a, b, Map.of(p, user));

        assertEquals(1, out.get("applications"));
        assertEquals(0.5, (double) out.get("meanA"), 1e-9);
        assertEquals(1.0, (double) out.get("meanB"), 1e-9);
    }

    @Test
    void jobMatchIgnoresOtherUsersBullets() {
        UUID user = UUID.randomUUID(), other = UUID.randomUUID(), p = UUID.randomUUID();
        Application app = new Application();
        app.setUserId(user);
        app.setAtsMatched(new String[]{"Kafka"});
        List<EvalItem> a = List.of(new EvalItem(p, "P", "PROJECT", "backend", "PENDING", List.of(), "Ran Kafka."));
        Map<String, Object> out = EvalService.jobMatch(List.of(app), a, a, Map.of(p, other));
        assertEquals(0.0, (double) out.get("meanA"), 1e-9);
    }
}
