CREATE TABLE app_user (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(64)  UNIQUE NOT NULL,
    email         VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Seed user for existing data. Fixed UUID so Phase 3 migration can reference it safely.
-- Credentials overridden at runtime via ApplicationRunner; this row just satisfies FK constraints.
INSERT INTO app_user (id, username, email, password_hash)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'seed',
    'seed@localhost',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy'
);
