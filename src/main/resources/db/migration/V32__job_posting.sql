-- Shared intern-job feed pushed in by the external Jobs API webhook. Not per-user:
-- anyone can browse it, and tailoring one goes through the normal application flow.
CREATE TABLE job_posting (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source      TEXT NOT NULL,
    external_id TEXT NOT NULL,
    title       TEXT,
    company     TEXT,
    location    TEXT,
    posted      TEXT,
    spotted     TEXT,
    url         TEXT NOT NULL,
    company_url TEXT,
    role        TEXT,
    stack       TEXT[] NOT NULL DEFAULT '{}',
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (source, external_id)
);
CREATE INDEX job_posting_received_idx ON job_posting (received_at DESC);
