package com.resumepipeline.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    List<Project> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    List<Project> findAllByUserIdAndKindOrderByCreatedAtDesc(UUID userId, Project.Kind kind);
    Optional<Project> findByUserIdAndId(UUID userId, UUID id);
    // Targeted lookup used in ApplicationService — projectIds already scoped by user via bullet filter.
    List<Project> findByIdIn(Collection<UUID> ids);
}
