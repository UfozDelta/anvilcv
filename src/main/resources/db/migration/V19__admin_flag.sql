ALTER TABLE app_user ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT FALSE;

-- The seed user (fixed UUID inserted by V6) becomes the first admin. SeedUserRunner
-- re-asserts the flag on every boot, so it cannot be lost by a manual UPDATE.
UPDATE app_user SET is_admin = TRUE WHERE id = '00000000-0000-0000-0000-000000000001';
