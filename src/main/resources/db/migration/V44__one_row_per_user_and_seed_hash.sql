-- V7 dropped the singleton constraints and left only non-unique user_id indexes, so a race on
-- first access could create two rows and findByUserId would then throw forever.
DROP INDEX IF EXISTS profile_user_id_idx;
DROP INDEX IF EXISTS generation_config_user_id_idx;
CREATE UNIQUE INDEX profile_user_id_uq           ON profile(user_id);
CREATE UNIQUE INDEX generation_config_user_id_uq ON generation_config(user_id);

-- V6 shipped a known bcrypt hash for the seed user. SeedUserRunner replaces it on boot when
-- SEED_PASSWORD is set; until then no password should match.
UPDATE app_user SET password_hash = '!' WHERE id = '00000000-0000-0000-0000-000000000001';
