ALTER TABLE application
    ADD COLUMN selected_skills JSONB NOT NULL DEFAULT '{}';
