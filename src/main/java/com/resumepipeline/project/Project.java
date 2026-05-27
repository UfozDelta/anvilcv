package com.resumepipeline.project;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project")
public class Project {

    public enum Kind { PROJECT, EXPERIENCE }

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind = Kind.PROJECT;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "source_path")
    private String sourcePath;

    // Used when kind = EXPERIENCE
    private String title;
    private String company;
    private String location;
    private String dates;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public Project() {}

    public Project(UUID userId, Kind kind, String name, String description, String sourcePath,
                   String title, String company, String location, String dates) {
        this.userId = userId;
        this.kind = kind == null ? Kind.PROJECT : kind;
        this.name = name;
        this.description = description;
        this.sourcePath = sourcePath;
        this.title = title;
        this.company = company;
        this.location = location;
        this.dates = dates;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind == null ? Kind.PROJECT : kind; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSourcePath() { return sourcePath; }
    public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getDates() { return dates; }
    public void setDates(String dates) { this.dates = dates; }
    public Instant getCreatedAt() { return createdAt; }
}
