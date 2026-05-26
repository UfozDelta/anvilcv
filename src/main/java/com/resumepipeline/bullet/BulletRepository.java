package com.resumepipeline.bullet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BulletRepository extends JpaRepository<Bullet, UUID> {
    List<Bullet> findByProjectIdOrderByCreatedAtAsc(UUID projectId);
    long countByProjectId(UUID projectId);
    void deleteByProjectId(UUID projectId);
}
