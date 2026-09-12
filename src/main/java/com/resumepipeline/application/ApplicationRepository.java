package com.resumepipeline.application;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {
    List<Application> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    List<Application> findByUserIdAndOutcomeOrderByCreatedAtDesc(UUID userId, String outcome);
    Optional<Application> findByUserIdAndId(UUID userId, UUID id);

    @Query("SELECT a FROM Application a ORDER BY a.createdAt DESC")
    List<Application> findAllOrderByCreatedAtDesc();

    @Query("SELECT AVG(a.pipelineDurationMs) FROM Application a WHERE a.pipelineDurationMs IS NOT NULL")
    Double avgPipelineDurationMs();

    long countByPipelineDurationMsNotNull();

    /**
     * Flag every scored page of this user's that renders any of the given bullets.
     *
     * <p>Native because {@code selected_bullet_ids} is a Postgres {@code uuid[]} column rather
     * than a join table, and no derived-query form reaches inside an array. {@code &&} is
     * array-overlap, so one statement covers a whole batch of rewritten bullets, and no
     * application row (each carrying a PDF and a LaTeX blob) has to be loaded to flag it.
     *
     * <p>{@code recruiter_score is not null} matters: an application that was never scored is a
     * different state from one whose score went out of date, and the UI renders them
     * differently. Marking a never-scored page "stale" would tell the user their score predates
     * an edit when there was never a score at all.
     *
     * <p>The ids arrive as one comma-joined string rather than a bound {@code UUID[]} on
     * purpose. Array binding through a native query depends on Hibernate's parameter handling,
     * and this project has no integration test able to catch it failing - there is no
     * Testcontainers or in-memory Postgres in the suite, so every native query here first runs
     * for real against production. A single string parameter has no such ambiguity.
     * {@code cast(... as uuid[])} rather than {@code ::uuid[]} because Hibernate reads a
     * {@code ::} in native SQL as a parameter marker.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update applications set recruiter_stale = true
            where user_id = :userId
              and recruiter_score is not null
              and recruiter_stale = false
              and selected_bullet_ids && cast(string_to_array(:bulletIds, ',') as uuid[])
            """, nativeQuery = true)
    int markRecruiterStaleForBullets(@Param("userId") UUID userId, @Param("bulletIds") String bulletIds);
}
