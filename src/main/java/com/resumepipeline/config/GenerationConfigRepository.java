package com.resumepipeline.config;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface GenerationConfigRepository extends JpaRepository<GenerationConfig, UUID> {
    Optional<GenerationConfig> findByUserId(UUID userId);
}
