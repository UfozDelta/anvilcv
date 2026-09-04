ALTER TABLE application
    ADD COLUMN locked_bullet_ids uuid[] NOT NULL DEFAULT '{}',
    ADD COLUMN version bigint NOT NULL DEFAULT 0;
