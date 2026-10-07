package com.resumepipeline.application;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
     *
     * <p>{@code @Transactional} here because callers (BulletService) run outside a transaction,
     * and a modifying query without one throws.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update application set recruiter_stale = true
            where user_id = :userId
              and recruiter_score is not null
              and recruiter_stale = false
              and selected_bullet_ids && cast(string_to_array(:bulletIds, ',') as uuid[])
            """, nativeQuery = true)
    int markRecruiterStaleForBullets(@Param("userId") UUID userId, @Param("bulletIds") String bulletIds);

    // pdf_stale is written only by these statements (it is updatable=false on the entity), so a
    // whole-row save of an Application loaded before an edit cannot wipe it. Same native-SQL and
    // comma-joined-id conventions as markRecruiterStaleForBullets above. Only rows that have a
    // PDF are flagged: with no PDF there is nothing to be out of date. Every mark bumps
    // pdf_stale_seq, even on a row already stale, so clearPdfStale can tell a mark landed.

    /** Pages that print any of these bullets. */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query(value = """
            update application set pdf_stale = true, pdf_stale_seq = pdf_stale_seq + 1
            where user_id = :userId
              and pdf_blob is not null
              and selected_bullet_ids && cast(string_to_array(:bulletIds, ',') as uuid[])
            """, nativeQuery = true)
    int markPdfStaleForBullets(@Param("userId") UUID userId, @Param("bulletIds") String bulletIds);

    /**
     * Pages that print any bullet of this project, and so its heading. REQUIRES_NEW because
     * ProjectService.delete calls it inside its own transaction and swallows a failure here;
     * joining that transaction would mark it rollback-only and fail the delete anyway.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying(flushAutomatically = true)
    @Query(value = """
            update application set pdf_stale = true, pdf_stale_seq = pdf_stale_seq + 1
            where user_id = :userId
              and pdf_blob is not null
              and selected_bullet_ids && array(select b.id from bullet b where b.project_id = :projectId)
            """, nativeQuery = true)
    int markPdfStaleForProject(@Param("userId") UUID userId, @Param("projectId") UUID projectId);

    /** Every page of this user's: the profile header and education print on all of them. */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query(value = """
            update application set pdf_stale = true, pdf_stale_seq = pdf_stale_seq + 1
            where user_id = :userId
              and pdf_blob is not null
            """, nativeQuery = true)
    int markPdfStaleForUser(@Param("userId") UUID userId);

    /** After a good compile: clears the flag unless a mark landed since {@code seen} was read. */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query(value = "update application set pdf_stale = false where id = :id and pdf_stale_seq = :seen",
            nativeQuery = true)
    int clearPdfStale(@Param("id") UUID id, @Param("seen") long seen);

    /** After a failed compile: the old PDF, if any, now sits under a new selection. */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query(value = "update application set pdf_stale = (pdf_blob is not null) where id = :id", nativeQuery = true)
    int markPdfStaleAfterFailedCompile(@Param("id") UUID id);
}
