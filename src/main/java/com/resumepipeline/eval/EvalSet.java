package com.resumepipeline.eval;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** One bullet bank under evaluation: the frozen baseline or a dry-run generation. See V37. */
@Entity
@Table(name = "eval_set")
public class EvalSet {

    public static final String BASELINE = "BASELINE";
    public static final String GENERATED = "GENERATED";
    public static final String RUNNING = "RUNNING";
    public static final String DONE = "DONE";
    public static final String FAILED = "FAILED";

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String label;

    @Column(nullable = false)
    private String source;

    private String note;

    @Column(nullable = false)
    private String status;

    private String error;

    /** JSON array of {@link EvalItem}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String items = "[]";

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected EvalSet() {}

    public EvalSet(String label, String source, String note, String status) {
        this.label = label;
        this.source = source;
        this.note = note;
        this.status = status;
    }

    public UUID getId() { return id; }
    public String getLabel() { return label; }
    public String getSource() { return source; }
    public String getNote() { return note; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public String getItems() { return items; }
    public void setItems(String items) { this.items = items; }
    public Instant getCreatedAt() { return createdAt; }
}
