package com.resumepipeline.jobs;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface JobPostingRepository extends JpaRepository<JobPosting, UUID>, JpaSpecificationExecutor<JobPosting> {

    boolean existsBySourceAndExternalId(String source, String externalId);

    // saved_job is a bare (user, posting) pair, so it is reached through native queries
    // here instead of getting an entity of its own.

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO saved_job (user_id, job_posting_id) VALUES (:userId, :jobId) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    void save(@Param("userId") UUID userId, @Param("jobId") UUID jobId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM saved_job WHERE user_id = :userId AND job_posting_id = :jobId", nativeQuery = true)
    void unsave(@Param("userId") UUID userId, @Param("jobId") UUID jobId);

    @Query(value = "SELECT job_posting_id FROM saved_job WHERE user_id = :userId", nativeQuery = true)
    List<UUID> savedIds(@Param("userId") UUID userId);

    @Query(value = "SELECT job_posting_id FROM saved_job WHERE user_id = :userId AND job_posting_id IN (:jobIds)",
            nativeQuery = true)
    List<UUID> savedIdsAmong(@Param("userId") UUID userId, @Param("jobIds") Collection<UUID> jobIds);
}
