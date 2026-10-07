package com.resumepipeline.eval;

import com.resumepipeline.application.Application;
import com.resumepipeline.bullet.Bullet;
import com.resumepipeline.bullet.BulletRepository;
import com.resumepipeline.project.Project;
import com.resumepipeline.project.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EvalServiceTest {

    @Test
    void snapshotCopiesTheUsersBulletsIntoADoneBaselineSet() throws Exception {
        UUID user = UUID.randomUUID();
        Project p = new Project();
        p.setUserId(user);
        p.setName("Demo Shop");
        p.setKind(Project.Kind.PROJECT);
        ReflectionTestUtils.setField(p, "id", UUID.randomUUID());
        Bullet kept = new Bullet(p.getId(), "Built a checkout API in Spring Boot.", new String[]{"java"}, "backend");
        Bullet rejected = new Bullet(p.getId(), "Wrote 40 unit tests.", new String[0], "backend");
        rejected.setStatus("REJECTED");

        EvalSetRepository sets = mock(EvalSetRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        BulletRepository bullets = mock(BulletRepository.class);
        when(projects.findAllByUserIdOrderByCreatedAtDesc(user)).thenReturn(List.of(p));
        when(bullets.findByProjectIdOrderByCreatedAtAsc(p.getId())).thenReturn(List.of(kept, rejected));
        when(sets.save(any())).thenAnswer(inv -> inv.getArgument(0));
        EvalService svc = new EvalService(sets, projects, bullets, null, null, null);

        EvalSet s = svc.snapshotBank(user);

        assertEquals(EvalSet.BASELINE, s.getSource());
        assertEquals(EvalSet.DONE, s.getStatus());
        assertTrue(s.getLabel().startsWith("bank-" + LocalDate.now(ZoneOffset.UTC)));
        List<EvalItem> items = svc.items(s);
        assertEquals(2, items.size());
        assertEquals(new EvalItem(p.getId(), "Demo Shop", "PROJECT", "backend", "PENDING", List.of("java"),
                "Built a checkout API in Spring Boot.", null), items.get(0));
        assertEquals("REJECTED", items.get(1).status());
        verify(bullets, never()).save(any());
        verify(bullets, never()).delete(any());
    }

    @Test
    void snapshotOfAnEmptyBankIsRejected() {
        EvalService svc = new EvalService(mock(EvalSetRepository.class), mock(ProjectRepository.class),
                mock(BulletRepository.class), null, null, null);
        assertThrows(ResponseStatusException.class, () -> svc.snapshotBank(UUID.randomUUID()));
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
                "Deployed services on K8s.", null));
        List<EvalItem> b = List.of(new EvalItem(p, "P", "PROJECT", "backend", "PENDING", List.of(),
                "Deployed services on K8s fed by Kafka streams.", null));

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
        List<EvalItem> a = List.of(new EvalItem(p, "P", "PROJECT", "backend", "PENDING", List.of(), "Ran Kafka.", null));
        Map<String, Object> out = EvalService.jobMatch(List.of(app), a, a, Map.of(p, other));
        assertEquals(0.0, (double) out.get("meanA"), 1e-9);
    }

    private static EvalItem item(UUID p, String status, String text, UUID storyId) {
        return new EvalItem(p, "P", "PROJECT", "backend", status, List.of(), text, storyId);
    }

    @Test
    void coverageSkipsRejectedAndUnreviewedVanityBullets() {
        UUID user = UUID.randomUUID(), p = UUID.randomUUID();
        Set<String> kw = Set.of("kafka", "redis", "grpc");
        List<EvalItem> items = List.of(
                item(p, "REJECTED", "Streamed orders through Kafka topics.", null),
                item(p, "PENDING", "Cached sessions in Redis over 87 commits.", null),
                item(p, "APPROVED", "Served quotes over gRPC across 40 unit tests.", null));

        // Only the approved one counts: rejected never ranks, unreviewed vanity is filtered.
        assertEquals(1.0 / 3, EvalService.coverage(items, user, Map.of(p, user), kw, List.of()), 1e-9);
    }

    @Test
    void coverageCollapsesWordingsOfOneStory() {
        UUID user = UUID.randomUUID(), p = UUID.randomUUID(), story = UUID.randomUUID();
        Set<String> kw = Set.of("kafka", "redis");
        List<EvalItem> items = List.of(
                item(p, "PENDING", "Streamed orders through Kafka topics.", story),
                item(p, "PENDING", "Cut checkout latency with a Redis cache.", story));

        assertEquals(0.5, EvalService.coverage(items, user, Map.of(p, user), kw, List.of()), 1e-9);
    }

    @Test
    void itemsSavedBeforeStoryIdReadItAsNull() throws Exception {
        EvalSet s = new EvalSet("x", EvalSet.BASELINE, null, EvalSet.DONE);
        s.setItems("[{\"projectId\":\"" + UUID.randomUUID() + "\",\"projectName\":\"P\",\"projectKind\":\"PROJECT\","
                + "\"category\":\"backend\",\"status\":\"PENDING\",\"tags\":[],\"text\":\"t\"}]");
        EvalService svc = new EvalService(mock(EvalSetRepository.class), null, null, null, null, null);
        assertNull(svc.items(s).get(0).storyId());
    }
}
