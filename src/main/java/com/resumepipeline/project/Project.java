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

    @Column(name = "github_url")
    private String githubUrl;

    @Column(name = "repo_context", columnDefinition = "text")
    private String repoContext;

    // Enrichment fields — user-provided context for better bullet generation
    @Column(name = "tech_stack")
    private String techStack;

    @Column(name = "your_role")
    private String yourRole;

    @Column(columnDefinition = "text")
    private String ownership;

    @Column(name = "scale_impact")
    private String scaleImpact;

    @Column(name = "hardest_problem", columnDefinition = "text")
    private String hardestProblem;

    @Column(name = "technical_decisions", columnDefinition = "text")
    private String technicalDecisions;

    @Column(name = "user_impact", columnDefinition = "text")
    private String userImpact;

    @Column(name = "security_posture", columnDefinition = "text")
    private String securityPosture;

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
    public String getGithubUrl() { return githubUrl; }
    public void setGithubUrl(String githubUrl) { this.githubUrl = githubUrl; }
    public String getRepoContext() { return repoContext; }
    public void setRepoContext(String repoContext) { this.repoContext = repoContext; }
    public String getTechStack() { return techStack; }
    public void setTechStack(String techStack) { this.techStack = techStack; }
    public String getYourRole() { return yourRole; }
    public void setYourRole(String yourRole) { this.yourRole = yourRole; }
    public String getOwnership() { return ownership; }
    public void setOwnership(String ownership) { this.ownership = ownership; }
    public String getScaleImpact() { return scaleImpact; }
    public void setScaleImpact(String scaleImpact) { this.scaleImpact = scaleImpact; }
    public String getHardestProblem() { return hardestProblem; }
    public void setHardestProblem(String hardestProblem) { this.hardestProblem = hardestProblem; }
    public String getTechnicalDecisions() { return technicalDecisions; }
    public void setTechnicalDecisions(String technicalDecisions) { this.technicalDecisions = technicalDecisions; }
    public String getUserImpact() { return userImpact; }
    public void setUserImpact(String userImpact) { this.userImpact = userImpact; }
    public String getSecurityPosture() { return securityPosture; }
    public void setSecurityPosture(String securityPosture) { this.securityPosture = securityPosture; }
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
