package com.resumepipeline.llm;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "llm_usage_log")
public class LlmUsageLog {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String source;

    @Column(name = "prompt_tokens", nullable = false)
    private int promptTokens;

    @Column(name = "candidates_tokens", nullable = false)
    private int candidatesTokens;

    @Column(name = "cost_usd", nullable = false)
    private BigDecimal costUsd;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected LlmUsageLog() {}

    public LlmUsageLog(UUID userId, String source, int promptTokens, int candidatesTokens,
                       BigDecimal costUsd, UUID applicationId, UUID projectId) {
        this.userId = userId;
        this.source = source;
        this.promptTokens = promptTokens;
        this.candidatesTokens = candidatesTokens;
        this.costUsd = costUsd;
        this.applicationId = applicationId;
        this.projectId = projectId;
    }

    public UUID getId()               { return id; }
    public UUID getUserId()           { return userId; }
    public String getSource()         { return source; }
    public int getPromptTokens()      { return promptTokens; }
    public int getCandidatesTokens()  { return candidatesTokens; }
    public BigDecimal getCostUsd()    { return costUsd; }
    public UUID getApplicationId()    { return applicationId; }
    public UUID getProjectId()        { return projectId; }
    public Instant getCreatedAt()     { return createdAt; }
}
