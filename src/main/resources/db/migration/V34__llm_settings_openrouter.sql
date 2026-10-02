-- OpenRouter as its own provider (not just a base URL on the generic openai one), so its
-- request extras - reasoning control, provider routing, real per-call cost - can be sent.
-- Same rule as V20: NULL means "fall back to application.yml".
ALTER TABLE llm_settings
    ADD COLUMN openrouter_api_key_enc     TEXT,
    ADD COLUMN openrouter_base_url        TEXT,
    ADD COLUMN openrouter_model_generate  TEXT,
    ADD COLUMN openrouter_model_match     TEXT,
    ADD COLUMN openrouter_model_clean_jd  TEXT;
