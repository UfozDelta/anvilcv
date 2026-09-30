package com.resumepipeline.github;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "github_installation")
public class GithubInstallation {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "installation_id", nullable = false)
    private long installationId;

    @Column(name = "account_login", nullable = false)
    private String accountLogin;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public GithubInstallation() {}

    public GithubInstallation(UUID userId, long installationId, String accountLogin) {
        this.userId = userId;
        this.installationId = installationId;
        this.accountLogin = accountLogin;
    }

    public UUID getUserId() { return userId; }
    public long getInstallationId() { return installationId; }
    public void setInstallationId(long installationId) { this.installationId = installationId; }
    public String getAccountLogin() { return accountLogin; }
    public void setAccountLogin(String accountLogin) { this.accountLogin = accountLogin; }
}
