package com.resumepipeline.bullet;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** The work a group of wordings (bullets sharing {@code storyId}) describes. See V41. */
@Entity
@Table(name = "story")
public class Story {

    // Assigned in Java, not @GeneratedValue: bullets carry the id before the row is saved, and
    // the eval dry run's stand-in save hands the entity back without generating one.
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]", nullable = false)
    private String[] evidence = new String[0];

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]", nullable = false)
    private String[] lenses = new String[0];

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Story() {}

    public Story(UUID id, UUID projectId, String title, String[] evidence, String[] lenses) {
        this.id = id;
        this.projectId = projectId;
        this.title = title;
        this.evidence = evidence == null ? new String[0] : evidence;
        this.lenses = lenses == null ? new String[0] : lenses;
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public String getTitle() { return title; }
    public String[] getEvidence() { return evidence; }
    public String[] getLenses() { return lenses; }
    public void setLenses(String[] lenses) { this.lenses = lenses; }
    public Instant getCreatedAt() { return createdAt; }
}
