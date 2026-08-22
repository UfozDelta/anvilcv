package com.resumepipeline.application;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outcome_history")
public class OutcomeHistory {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(nullable = false)
    private String outcome;

    @Column(name = "changed_at", nullable = false, insertable = false, updatable = false)
    private Instant changedAt;

    protected OutcomeHistory() {}

    public OutcomeHistory(UUID applicationId, String outcome) {
        this.applicationId = applicationId;
        this.outcome = outcome;
    }

    public UUID getId()            { return id; }
    public UUID getApplicationId() { return applicationId; }
    public String getOutcome()     { return outcome; }
    public Instant getChangedAt()  { return changedAt; }
}
