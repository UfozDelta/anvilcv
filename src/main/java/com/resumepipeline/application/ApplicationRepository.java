package com.resumepipeline.application;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {
    List<Application> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    List<Application> findByUserIdAndOutcomeOrderByCreatedAtDesc(UUID userId, String outcome);
    Optional<Application> findByUserIdAndId(UUID userId, UUID id);
}
