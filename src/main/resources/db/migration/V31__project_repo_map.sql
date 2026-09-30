-- Hierarchical repo map (JSON, see RepoMap): project -> subsystems -> modules -> symbols plus
-- facts counted from the code. Tied to repo_commit_sha; rebuilt only when that changes.
ALTER TABLE project ADD COLUMN repo_map TEXT;
