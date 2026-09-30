-- GitHub App link, one per user. No token is stored: installation tokens are minted on
-- demand from the app's private key and live ~1h in memory, so there is nothing secret
-- here to encrypt. The installation id alone grants nothing without that key.
CREATE TABLE github_installation (
    user_id         UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    installation_id BIGINT      NOT NULL,
    account_login   TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Repo a project was explored from, pinned to the commit the explorer read, plus the
-- verbatim evidence spans (JSON array) that bullets get traced back to.
ALTER TABLE project ADD COLUMN repo_branch     TEXT;
ALTER TABLE project ADD COLUMN repo_commit_sha TEXT;
ALTER TABLE project ADD COLUMN repo_evidence   TEXT;
