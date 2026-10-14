package com.resumepipeline.quota;

import java.time.Instant;

/** The user's plan has no room left for this. Answered as 402 so the UI can offer an upgrade. */
public class QuotaExceededException extends RuntimeException {

    private final String kind;
    private final Plan plan;
    private final int limit;
    private final long used;
    private final Instant resetsAt;

    public QuotaExceededException(String message, String kind, Plan plan, int limit, long used, Instant resetsAt) {
        super(message);
        this.kind = kind;
        this.plan = plan;
        this.limit = limit;
        this.used = used;
        this.resetsAt = resetsAt;
    }

    public String kind() { return kind; }
    public Plan plan() { return plan; }
    public int limit() { return limit; }
    public long used() { return used; }
    /** When the allowance comes back; null for a one-off pack, which does not reset. */
    public Instant resetsAt() { return resetsAt; }
}
