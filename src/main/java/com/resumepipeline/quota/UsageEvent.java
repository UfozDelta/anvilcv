package com.resumepipeline.quota;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** One finished tailor or generation, kept so the count survives deleting the thing it made. */
@Entity
@Table(name = "usage_event")
public class UsageEvent {

    public static final String TAILOR = "tailor";
    public static final String GENERATION = "generation";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String kind;

    @Column(name = "ref_id")
    private UUID refId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected UsageEvent() {}

    public UsageEvent(UUID userId, String kind, UUID refId) {
        this.userId = userId;
        this.kind = kind;
        this.refId = refId;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getKind() { return kind; }
    public UUID getRefId() { return refId; }
    public Instant getCreatedAt() { return createdAt; }
}
