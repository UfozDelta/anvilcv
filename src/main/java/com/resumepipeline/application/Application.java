package com.resumepipeline.application;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "application")
public class Application {

    @Id
    @GeneratedValue
    private UUID id;

    private String company;
    private String role;

    @Column(name = "jd_text", nullable = false, columnDefinition = "text")
    private String jdText;

    @Column(name = "jd_url")
    private String jdUrl;

    @Column(name = "role_emphasis", nullable = false)
    private String roleEmphasis;

    /** Full ranked list as JSON: [{bulletId, rank, why}, ...]. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bullet_ranking", columnDefinition = "jsonb", nullable = false)
    private String bulletRanking = "[]";

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "selected_bullet_ids", columnDefinition = "uuid[]", nullable = false)
    private UUID[] selectedBulletIds = new UUID[0];

    @Column(name = "cover_letter", columnDefinition = "text")
    private String coverLetter;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "ats_matched", columnDefinition = "text[]", nullable = false)
    private String[] atsMatched = new String[0];

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "ats_missing", columnDefinition = "text[]", nullable = false)
    private String[] atsMissing = new String[0];

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "selected_courses", columnDefinition = "text[]", nullable = false)
    private String[] selectedCourses = new String[0];

    /** JSON map: {languages:[...], frameworks:[...], databases:[...], devops:[...]} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_skills", columnDefinition = "jsonb", nullable = false)
    private String selectedSkills = "{}";

    @Column(name = "tex_blob")
    private byte[] texBlob;

    @Column(name = "pdf_blob")
    private byte[] pdfBlob;

    @Column(name = "tectonic_log", columnDefinition = "text")
    private String tectonicLog;

    @Column(nullable = false)
    private String outcome = "applied";

    @Column(name = "llm_prompt_tokens", nullable = false)
    private int llmPromptTokens = 0;

    @Column(name = "llm_candidates_tokens", nullable = false)
    private int llmCandidatesTokens = 0;

    @Column(name = "llm_cost_usd", nullable = false)
    private java.math.BigDecimal llmCostUsd = java.math.BigDecimal.ZERO;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public Application() {}

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getJdText() { return jdText; }
    public void setJdText(String jdText) { this.jdText = jdText; }
    public String getJdUrl() { return jdUrl; }
    public void setJdUrl(String jdUrl) { this.jdUrl = jdUrl; }
    public String getRoleEmphasis() { return roleEmphasis; }
    public void setRoleEmphasis(String roleEmphasis) { this.roleEmphasis = roleEmphasis; }
    public String getBulletRanking() { return bulletRanking; }
    public void setBulletRanking(String bulletRanking) { this.bulletRanking = bulletRanking; }
    public UUID[] getSelectedBulletIds() { return selectedBulletIds; }
    public void setSelectedBulletIds(UUID[] selectedBulletIds) {
        this.selectedBulletIds = selectedBulletIds == null ? new UUID[0] : selectedBulletIds;
    }
    public String getCoverLetter() { return coverLetter; }
    public void setCoverLetter(String coverLetter) { this.coverLetter = coverLetter; }
    public String[] getAtsMatched() { return atsMatched; }
    public void setAtsMatched(String[] atsMatched) { this.atsMatched = atsMatched == null ? new String[0] : atsMatched; }
    public String[] getAtsMissing() { return atsMissing; }
    public void setAtsMissing(String[] atsMissing) { this.atsMissing = atsMissing == null ? new String[0] : atsMissing; }
    public String[] getSelectedCourses() { return selectedCourses; }
    public void setSelectedCourses(String[] selectedCourses) { this.selectedCourses = selectedCourses == null ? new String[0] : selectedCourses; }
    public String getSelectedSkills() { return selectedSkills; }
    public void setSelectedSkills(String selectedSkills) { this.selectedSkills = selectedSkills == null ? "{}" : selectedSkills; }
    public byte[] getTexBlob() { return texBlob; }
    public void setTexBlob(byte[] texBlob) { this.texBlob = texBlob; }
    public byte[] getPdfBlob() { return pdfBlob; }
    public void setPdfBlob(byte[] pdfBlob) { this.pdfBlob = pdfBlob; }
    public String getTectonicLog() { return tectonicLog; }
    public void setTectonicLog(String tectonicLog) { this.tectonicLog = tectonicLog; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public int getLlmPromptTokens() { return llmPromptTokens; }
    public void setLlmPromptTokens(int llmPromptTokens) { this.llmPromptTokens = llmPromptTokens; }
    public int getLlmCandidatesTokens() { return llmCandidatesTokens; }
    public void setLlmCandidatesTokens(int llmCandidatesTokens) { this.llmCandidatesTokens = llmCandidatesTokens; }
    public java.math.BigDecimal getLlmCostUsd() { return llmCostUsd; }
    public void setLlmCostUsd(java.math.BigDecimal llmCostUsd) { this.llmCostUsd = llmCostUsd; }
    public Instant getCreatedAt() { return createdAt; }
}
