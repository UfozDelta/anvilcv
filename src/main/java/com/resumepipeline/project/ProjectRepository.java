package com.resumepipeline.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    List<Project> findByKindOrderByCreatedAtDesc(Project.Kind kind);
    // Targeted lookup — avoids loading all projects when only a subset are needed.
    List<Project> findByIdIn(java.util.Collection<UUID> ids);
}
