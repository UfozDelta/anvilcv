-- Per-user bookmarks on the shared job feed; /jobs filters by them.
CREATE TABLE saved_job (
    user_id        UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    job_posting_id UUID NOT NULL REFERENCES job_posting (id) ON DELETE CASCADE,
    saved_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, job_posting_id)
);
