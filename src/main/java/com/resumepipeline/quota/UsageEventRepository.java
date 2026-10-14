package com.resumepipeline.quota;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface UsageEventRepository extends JpaRepository<UsageEvent, UUID> {

    long countByUserIdAndKindAndCreatedAtGreaterThanEqual(UUID userId, String kind, Instant since);

    boolean existsByUserIdAndKindAndRefId(UUID userId, String kind, UUID refId);

    /** Projects of one kind (PROJECT or EXPERIENCE) this user has had bullets written for. */
    @Query(value = "SELECT count(DISTINCT e.ref_id) FROM usage_event e JOIN project p ON p.id = e.ref_id "
                 + "WHERE e.user_id = :userId AND e.kind = 'generation' AND p.kind = :projectKind", nativeQuery = true)
    long countGeneratedEntries(@Param("userId") UUID userId, @Param("projectKind") String projectKind);
}
