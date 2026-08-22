CREATE TABLE outcome_history (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL REFERENCES application(id) ON DELETE CASCADE,
    outcome        TEXT NOT NULL,
    changed_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX outcome_history_application_idx ON outcome_history (application_id, changed_at);
