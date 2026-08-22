package com.resumepipeline.bullet;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bullet")
public class Bullet {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]", nullable = false)
    private String[] tags = new String[0];

    @Column(nullable = false)
    private String category = "general";

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private Instant updatedAt;

    public Bullet() {}

    public Bullet(UUID projectId, String text, String[] tags) {
        this(projectId, text, tags, "general");
    }

    public Bullet(UUID projectId, String text, String[] tags, String category) {
        this.projectId = projectId;
        this.text = text;
        this.tags = tags == null ? new String[0] : tags;
        this.category = category == null || category.isBlank() ? "general" : category;
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String[] getTags() { return tags; }
    public void setTags(String[] tags) { this.tags = tags == null ? new String[0] : tags; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category == null || category.isBlank() ? "general" : category; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
