ALTER TABLE application
    ADD COLUMN llm_prompt_tokens     INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN llm_candidates_tokens INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN llm_cost_usd          NUMERIC(12, 8) NOT NULL DEFAULT 0;
