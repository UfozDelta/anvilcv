package com.resumepipeline.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class LlmUsageService {

    private static final Logger log = LoggerFactory.getLogger(LlmUsageService.class);

    private final LlmUsageLogRepository repo;

    public LlmUsageService(LlmUsageLogRepository repo) {
        this.repo = repo;
    }

    public void record(UUID userId, String source, TokenAccumulator tokens,
                       UUID applicationId, UUID projectId) {
        if (tokens == null || tokens.getPromptTokens() == 0) return;
        try {
            repo.save(new LlmUsageLog(
                    userId, source,
                    tokens.getPromptTokens(), tokens.getCandidatesTokens(), tokens.getCostUsd(),
                    applicationId, projectId));
        } catch (Exception e) {
            log.error("Failed to persist LLM usage log [source={} user={}]: {}", source, userId, e.getMessage());
        }
    }
}
