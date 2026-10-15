package com.resumepipeline.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A webhook event already handled, so a redelivery is skipped. */
@Entity
@Table(name = "stripe_event")
public class StripeEvent {

    @Id
    @Column(length = 80)
    private String id;

    @Column(nullable = false, length = 80)
    private String type;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt = Instant.now();

    protected StripeEvent() {}

    public StripeEvent(String id, String type) {
        this.id = id;
        this.type = type;
    }

    public String getId() { return id; }
}
