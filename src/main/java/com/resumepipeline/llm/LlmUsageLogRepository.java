package com.resumepipeline.llm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LlmUsageLogRepository extends JpaRepository<LlmUsageLog, UUID> {
    List<LlmUsageLog> findAllByUserId(UUID userId);
}
