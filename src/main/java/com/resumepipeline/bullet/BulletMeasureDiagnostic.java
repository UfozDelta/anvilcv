package com.resumepipeline.bullet;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Shadow-mode telemetry row: one bullet generation compared against both the char-count
 * band (already gating generation today) and the real tectonic measurement from
 * {@link BulletLineMeasurer}. Nothing acts on {@link #agree} yet -- this table exists so the
 * disagreement rate can be checked against real compiles before the real measurement is
 * trusted to drop anything. See BulletLineMeasurer's class javadoc.
 */
@Entity
@Table(name = "bullet_measure_diagnostic")
public class BulletMeasureDiagnostic {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String category;

    @Column(name = "bullet_text", nullable = false, columnDefinition = "text")
    private String bulletText;

    @Column(name = "char_count", nullable = false)
    private int charCount;

    @Column(name = "declared_kept", nullable = false)
    private boolean declaredKept;

    /** False when the batch compile failed or didn't return this bullet -- no real data below. */
    @Column(nullable = false)
    private boolean measured;

    @Column(name = "measured_lines")
    private Integer measuredLines;

    @Column(name = "measured_fill")
    private Double measuredFill;

    /** True when the real measurement's clean-fit verdict matches the char-count decision. */
    @Column(nullable = false)
    private boolean agree;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected BulletMeasureDiagnostic() {}

    public BulletMeasureDiagnostic(UUID projectId, UUID userId, String category, String bulletText,
                                   int charCount, boolean declaredKept, boolean measured,
                                   Integer measuredLines, Double measuredFill, boolean agree) {
        this.projectId = projectId;
        this.userId = userId;
        this.category = category;
        this.bulletText = bulletText;
        this.charCount = charCount;
        this.declaredKept = declaredKept;
        this.measured = measured;
        this.measuredLines = measuredLines;
        this.measuredFill = measuredFill;
        this.agree = agree;
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public UUID getUserId() { return userId; }
    public String getCategory() { return category; }
    public String getBulletText() { return bulletText; }
    public int getCharCount() { return charCount; }
    public boolean isDeclaredKept() { return declaredKept; }
    public boolean isMeasured() { return measured; }
    public Integer getMeasuredLines() { return measuredLines; }
    public Double getMeasuredFill() { return measuredFill; }
    public boolean isAgree() { return agree; }
    public Instant getCreatedAt() { return createdAt; }
}
