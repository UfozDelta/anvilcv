package com.resumepipeline.application;

import com.resumepipeline.bullet.Bullet;
import com.resumepipeline.llm.LlmClient;
import com.resumepipeline.project.Project;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * Test fixtures for selection/service tests.
 *
 * <p>{@link Bullet#getId()} and {@link Project#getId()} are JPA {@code @GeneratedValue}
 * with no setter, so ids are injected via reflection here. Builders force the caller
 * to supply every field the selection logic branches on ({@code tags}, {@code kind})
 * — no hidden defaults that tests might silently depend on.
 */
final class TestFixtures {

    private TestFixtures() {}

    /** Bullet with an explicit id, project, and tags (tags drive tag-score). */
    static Bullet bullet(UUID id, UUID projectId, String[] tags) {
        Bullet b = new Bullet(projectId, "text-" + id, tags == null ? new String[0] : tags, "general");
        setId(b, id);
        return b;
    }

    /** Project with an explicit id and kind (kind drives the kind-floor pass). */
    static Project project(UUID id, Project.Kind kind, String name) {
        Project p = new Project(UUID.randomUUID(), kind, name, "desc", null, null, null, null, null);
        setId(p, id);
        return p;
    }

    static LlmClient.RankedBullet ranked(UUID bulletId, int rank) {
        return new LlmClient.RankedBullet(bulletId.toString(), rank, "why-" + rank);
    }

    static LlmClient.RankedBullet rankedRaw(String bulletId, int rank) {
        return new LlmClient.RankedBullet(bulletId, rank, "why-" + rank);
    }

    private static void setId(Object entity, UUID id) {
        try {
            Field f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to set id on " + entity.getClass().getSimpleName(), e);
        }
    }
}
