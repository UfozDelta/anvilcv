-- Admin-managed LLM provider + credentials. One row, ever: the singleton column is
-- UNIQUE and always TRUE, so a second INSERT fails instead of silently splitting config.
--
-- Every provider column is nullable, and NULL means "fall back to application.yml".
-- That keeps existing deploys running untouched after this migration and leaves env
-- vars as a backstop if a bad save in the UI would otherwise lock the app out.
CREATE TABLE llm_settings (
    id                       UUID PRIMARY KEY,
    singleton                BOOLEAN NOT NULL DEFAULT TRUE,

    provider                 TEXT,

    gemini_api_key_enc       TEXT,
    gemini_model_generate    TEXT,
    gemini_model_match       TEXT,
    gemini_model_clean_jd    TEXT,

    opencode_api_key_enc     TEXT,
    opencode_base_url        TEXT,
    opencode_model_generate  TEXT,
    opencode_model_match     TEXT,
    opencode_model_clean_jd  TEXT,

    openai_api_key_enc       TEXT,
    openai_base_url          TEXT,
    openai_model_generate    TEXT,
    openai_model_match       TEXT,
    openai_model_clean_jd    TEXT,

    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by               TEXT,

    CONSTRAINT llm_settings_singleton UNIQUE (singleton)
);

INSERT INTO llm_settings (id, singleton) VALUES (gen_random_uuid(), TRUE);
