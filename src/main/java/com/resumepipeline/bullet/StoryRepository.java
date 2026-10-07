package com.resumepipeline.bullet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Not scoped by user: callers check project ownership via ProjectService.get first. */
public interface StoryRepository extends JpaRepository<Story, UUID> {
    List<Story> findByProjectIdOrderByCreatedAtAsc(UUID projectId);
}
