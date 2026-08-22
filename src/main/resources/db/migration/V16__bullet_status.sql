ALTER TABLE bullet ADD COLUMN status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED'));
CREATE INDEX bullet_project_status_idx ON bullet (project_id, status);
