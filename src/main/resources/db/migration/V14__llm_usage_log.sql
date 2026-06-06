CREATE TABLE llm_usage_log (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL,
    source            TEXT NOT NULL,
    prompt_tokens     INT NOT NULL DEFAULT 0,
    candidates_tokens INT NOT NULL DEFAULT 0,
    cost_usd          NUMERIC(12,8) NOT NULL DEFAULT 0,
    application_id    UUID REFERENCES application(id) ON DELETE SET NULL,
    project_id        UUID REFERENCES project(id)     ON DELETE SET NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_llm_usage_source_id CHECK (application_id IS NOT NULL OR project_id IS NOT NULL)
);
