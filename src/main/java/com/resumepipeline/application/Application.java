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

    /** Figures in the cover letter that trace to neither the selected bullets nor the JD. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "cover_letter_flags", columnDefinition = "text[]", nullable = false)
    private String[] coverLetterFlags = new String[0];

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

    /** Overall fit 0-100. Null when the scoring call failed — distinct from a genuine 0. */
    @Column(name = "fit_score")
    private Integer fitScore;

    @Column(name = "fit_verdict", columnDefinition = "text")
    private String fitVerdict;

    /** JSON map: {technical: n, experience: n} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fit_dimensions", columnDefinition = "jsonb", nullable = false)
    private String fitDimensions = "{}";

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "fit_strengths", columnDefinition = "text[]", nullable = false)
    private String[] fitStrengths = new String[0];

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "fit_gaps", columnDefinition = "text[]", nullable = false)
    private String[] fitGaps = new String[0];

    /**
     * Recruiter pass on the RENDERED page — a different question from {@link #fitScore},
     * which grades the candidate. Null when the call failed — distinct from a genuine 0.
     */
    @Column(name = "recruiter_score")
    private Integer recruiterScore;

    @Column(name = "recruiter_verdict", columnDefinition = "text")
    private String recruiterVerdict;

    /** JSON map: {evidenceStrength: n, relevanceDensity: n} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recruiter_dimensions", columnDefinition = "jsonb", nullable = false)
    private String recruiterDimensions = "{}";

    /** JSON array: [{bulletId, verdict, reason}, ...] */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recruiter_bullet_verdicts", columnDefinition = "jsonb", nullable = false)
    private String recruiterBulletVerdicts = "[]";

    /**
     * The recruiter pass's forced-negative output: it has no "looks fine" option and must
     * name a weakest bullet, the thinnest-supported JD requirement, and its objections.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "recruiter_weaknesses", columnDefinition = "text[]", nullable = false)
    private String[] recruiterWeaknesses = new String[0];

    @Column(name = "recruiter_thinnest_requirement", columnDefinition = "text")
    private String recruiterThinnestRequirement;

    /** Always one of the bullets that was actually rendered; validated before it is stored. */
    @Column(name = "recruiter_weakest_bullet_id")
    private UUID recruiterWeakestBulletId;

    /** True once a rerender changed the selection the recruiter pass was scored against. */
    @Column(name = "recruiter_stale", nullable = false)
    private boolean recruiterStale = false;

    /** Pages in the compiled PDF, from tectonic's log. Null when unknown. */
    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "tex_blob")
    private byte[] texBlob;

    @Column(name = "pdf_blob")
    private byte[] pdfBlob;

    @Column(name = "tectonic_log", columnDefinition = "text")
    private String tectonicLog;

    @Column(nullable = false)
    private String outcome = "applied";

    @Column(name = "pipeline_duration_ms")
    private Long pipelineDurationMs;

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
    public String[] getCoverLetterFlags() { return coverLetterFlags; }
    public void setCoverLetterFlags(String[] f) { this.coverLetterFlags = f == null ? new String[0] : f; }
    public void setCoverLetter(String coverLetter) { this.coverLetter = coverLetter; }
    public String[] getAtsMatched() { return atsMatched; }
    public void setAtsMatched(String[] atsMatched) { this.atsMatched = atsMatched == null ? new String[0] : atsMatched; }
    public String[] getAtsMissing() { return atsMissing; }
    public void setAtsMissing(String[] atsMissing) { this.atsMissing = atsMissing == null ? new String[0] : atsMissing; }
    public String[] getSelectedCourses() { return selectedCourses; }
    public void setSelectedCourses(String[] selectedCourses) { this.selectedCourses = selectedCourses == null ? new String[0] : selectedCourses; }
    public String getSelectedSkills() { return selectedSkills; }
    public void setSelectedSkills(String selectedSkills) { this.selectedSkills = selectedSkills == null ? "{}" : selectedSkills; }
    public Integer getFitScore() { return fitScore; }
    public void setFitScore(Integer fitScore) { this.fitScore = fitScore; }
    public String getFitVerdict() { return fitVerdict; }
    public void setFitVerdict(String fitVerdict) { this.fitVerdict = fitVerdict; }
    public String getFitDimensions() { return fitDimensions; }
    public void setFitDimensions(String fitDimensions) { this.fitDimensions = fitDimensions == null ? "{}" : fitDimensions; }
    public String[] getFitStrengths() { return fitStrengths; }
    public void setFitStrengths(String[] fitStrengths) { this.fitStrengths = fitStrengths == null ? new String[0] : fitStrengths; }
    public String[] getFitGaps() { return fitGaps; }
    public void setFitGaps(String[] fitGaps) { this.fitGaps = fitGaps == null ? new String[0] : fitGaps; }
    public Integer getRecruiterScore() { return recruiterScore; }
    public void setRecruiterScore(Integer recruiterScore) { this.recruiterScore = recruiterScore; }
    public String getRecruiterVerdict() { return recruiterVerdict; }
    public void setRecruiterVerdict(String recruiterVerdict) { this.recruiterVerdict = recruiterVerdict; }
    public String getRecruiterDimensions() { return recruiterDimensions; }
    public void setRecruiterDimensions(String recruiterDimensions) { this.recruiterDimensions = recruiterDimensions == null ? "{}" : recruiterDimensions; }
    public String getRecruiterBulletVerdicts() { return recruiterBulletVerdicts; }
    public void setRecruiterBulletVerdicts(String recruiterBulletVerdicts) { this.recruiterBulletVerdicts = recruiterBulletVerdicts == null ? "[]" : recruiterBulletVerdicts; }
    public String[] getRecruiterWeaknesses() { return recruiterWeaknesses; }
    public void setRecruiterWeaknesses(String[] w) { this.recruiterWeaknesses = w == null ? new String[0] : w; }
    public String getRecruiterThinnestRequirement() { return recruiterThinnestRequirement; }
    public void setRecruiterThinnestRequirement(String r) { this.recruiterThinnestRequirement = r; }
    public UUID getRecruiterWeakestBulletId() { return recruiterWeakestBulletId; }
    public void setRecruiterWeakestBulletId(UUID id) { this.recruiterWeakestBulletId = id; }
    public boolean isRecruiterStale() { return recruiterStale; }
    public void setRecruiterStale(boolean recruiterStale) { this.recruiterStale = recruiterStale; }
    public Integer getPageCount() { return pageCount; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }
    public byte[] getTexBlob() { return texBlob; }
    public void setTexBlob(byte[] texBlob) { this.texBlob = texBlob; }
    public byte[] getPdfBlob() { return pdfBlob; }
    public void setPdfBlob(byte[] pdfBlob) { this.pdfBlob = pdfBlob; }
    public String getTectonicLog() { return tectonicLog; }
    public void setTectonicLog(String tectonicLog) { this.tectonicLog = tectonicLog; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public Long getPipelineDurationMs() { return pipelineDurationMs; }
    public void setPipelineDurationMs(Long pipelineDurationMs) { this.pipelineDurationMs = pipelineDurationMs; }
    public int getLlmPromptTokens() { return llmPromptTokens; }
    public void setLlmPromptTokens(int llmPromptTokens) { this.llmPromptTokens = llmPromptTokens; }
    public int getLlmCandidatesTokens() { return llmCandidatesTokens; }
    public void setLlmCandidatesTokens(int llmCandidatesTokens) { this.llmCandidatesTokens = llmCandidatesTokens; }
    public java.math.BigDecimal getLlmCostUsd() { return llmCostUsd; }
    public void setLlmCostUsd(java.math.BigDecimal llmCostUsd) { this.llmCostUsd = llmCostUsd; }
    public Instant getCreatedAt() { return createdAt; }
}
