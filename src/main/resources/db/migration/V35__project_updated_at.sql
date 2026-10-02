-- Last-edit timestamp for the Projects/Experiences list; existing rows start at their creation time.
ALTER TABLE project ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE project SET updated_at = created_at;
ALTER TABLE project ALTER COLUMN updated_at SET NOT NULL;
ALTER TABLE project ALTER COLUMN updated_at SET DEFAULT now();
