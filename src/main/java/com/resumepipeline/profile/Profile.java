package com.resumepipeline.profile;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "profile")
public class Profile {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name = "";

    @Column(nullable = false)
    private String phone = "";

    @Column(nullable = false)
    private String email = "";

    @Column(name = "linkedin_handle", nullable = false)
    private String linkedinHandle = "";

    @Column(name = "github_handle", nullable = false)
    private String githubHandle = "";

    @Column(name = "portfolio_url")
    private String portfolioUrl;

    /** JSON array of {school, location, degree, dates, coursework}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String education = "[]";

    @Column(name = "skills_languages",  nullable = false) private String skillsLanguages  = "";
    @Column(name = "skills_frameworks", nullable = false) private String skillsFrameworks = "";
    @Column(name = "skills_databases",  nullable = false) private String skillsDatabases  = "";
    @Column(name = "skills_devops",     nullable = false) private String skillsDevops     = "";
    @Column(name = "skills_interests",  nullable = false) private String skillsInterests  = "";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String n) { this.name = n == null ? "" : n; }
    public String getPhone() { return phone; }
    public void setPhone(String p) { this.phone = p == null ? "" : p; }
    public String getEmail() { return email; }
    public void setEmail(String e) { this.email = e == null ? "" : e; }
    public String getLinkedinHandle() { return linkedinHandle; }
    public void setLinkedinHandle(String l) { this.linkedinHandle = l == null ? "" : l; }
    public String getGithubHandle() { return githubHandle; }
    public void setGithubHandle(String g) { this.githubHandle = g == null ? "" : g; }
    public String getPortfolioUrl() { return portfolioUrl; }
    public void setPortfolioUrl(String p) { this.portfolioUrl = (p == null || p.isBlank()) ? null : p; }
    public String getEducation() { return education; }
    public void setEducation(String e) { this.education = (e == null || e.isBlank()) ? "[]" : e; }
    public String getSkillsLanguages() { return skillsLanguages; }
    public void setSkillsLanguages(String s) { this.skillsLanguages = s == null ? "" : s; }
    public String getSkillsFrameworks() { return skillsFrameworks; }
    public void setSkillsFrameworks(String s) { this.skillsFrameworks = s == null ? "" : s; }
    public String getSkillsDatabases() { return skillsDatabases; }
    public void setSkillsDatabases(String s) { this.skillsDatabases = s == null ? "" : s; }
    public String getSkillsDevops() { return skillsDevops; }
    public void setSkillsDevops(String s) { this.skillsDevops = s == null ? "" : s; }
    public String getSkillsInterests() { return skillsInterests; }
    public void setSkillsInterests(String s) { this.skillsInterests = s == null ? "" : s; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant u) { this.updatedAt = u; }
}
