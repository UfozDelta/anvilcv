package com.resumepipeline.llm.settings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LlmSettingsRepository extends JpaRepository<LlmSettings, UUID> {
    /** The table holds exactly one row (UNIQUE singleton), so "first" is "the" row. */
    Optional<LlmSettings> findFirstByOrderByUpdatedAtAsc();
}
