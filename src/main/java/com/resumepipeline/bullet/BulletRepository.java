package com.resumepipeline.bullet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BulletRepository extends JpaRepository<Bullet, UUID> {
    List<Bullet> findByProjectIdOrderByCreatedAtAsc(UUID projectId);
    long countByProjectId(UUID projectId);
    void deleteByProjectId(UUID projectId);

    /**
     * Bullets belonging to projects owned by the given user that are eligible for a resume.
     * REJECTED bullets are excluded — the user triaged them off the bank, so matching must
     * never put them back on a document. PENDING stays eligible so untriaged banks still work.
     */
    @Query(value = "SELECT b.* FROM bullet b JOIN project p ON p.id = b.project_id "
                 + "WHERE p.user_id = :userId AND b.status <> 'REJECTED'", nativeQuery = true)
    List<Bullet> findSelectableByProjectUserId(@Param("userId") UUID userId);

    /** Scoped bulk fetch — only returns bullets whose project belongs to the given user. */
    @Query(value = "SELECT b.* FROM bullet b JOIN project p ON p.id = b.project_id WHERE b.id = ANY(:ids) AND p.user_id = :userId", nativeQuery = true)
    List<Bullet> findByIdsAndProjectUserId(@Param("ids") UUID[] ids, @Param("userId") UUID userId);
}
