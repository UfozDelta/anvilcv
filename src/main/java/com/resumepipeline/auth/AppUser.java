package com.resumepipeline.auth;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "is_admin", nullable = false)
    private boolean admin = false;

    @Column(name = "onboarded_at")
    private OffsetDateTime onboardedAt;

    @Column(name = "first_download_at")
    private OffsetDateTime firstDownloadAt;

    @Column(nullable = false, length = 20)
    private String plan = "free";

    @Column(name = "plan_started_at")
    private java.time.Instant planStartedAt;

    @Column(name = "quota_exempt", nullable = false)
    private boolean quotaExempt = false;

    protected AppUser() {}

    public AppUser(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public boolean isAdmin() { return admin; }
    public OffsetDateTime getOnboardedAt() { return onboardedAt; }
    public OffsetDateTime getFirstDownloadAt() { return firstDownloadAt; }
    public String getPlan() { return plan; }
    public java.time.Instant getPlanStartedAt() { return planStartedAt; }
    public boolean isQuotaExempt() { return quotaExempt; }

    public void setUsername(String username) { this.username = username; }
    public void setEmail(String email) { this.email = email; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setAdmin(boolean admin) { this.admin = admin; }
    public void setOnboardedAt(OffsetDateTime onboardedAt) { this.onboardedAt = onboardedAt; }
    public void setFirstDownloadAt(OffsetDateTime firstDownloadAt) { this.firstDownloadAt = firstDownloadAt; }
    public void setPlan(String plan) { this.plan = plan; }
    public void setPlanStartedAt(java.time.Instant planStartedAt) { this.planStartedAt = planStartedAt; }
    public void setQuotaExempt(boolean quotaExempt) { this.quotaExempt = quotaExempt; }
}
