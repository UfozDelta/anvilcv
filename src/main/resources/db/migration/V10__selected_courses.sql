ALTER TABLE application
    ADD COLUMN selected_courses TEXT[] NOT NULL DEFAULT '{}';
