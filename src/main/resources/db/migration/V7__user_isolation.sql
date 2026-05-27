-- Phase 3: add user_id FK to all data tables.
-- Existing rows are assigned to the seed user inserted by V6.

-- 1. Add nullable user_id columns
ALTER TABLE profile          ADD COLUMN user_id UUID REFERENCES app_user(id);
ALTER TABLE project          ADD COLUMN user_id UUID REFERENCES app_user(id);
ALTER TABLE application      ADD COLUMN user_id UUID REFERENCES app_user(id);
ALTER TABLE generation_config ADD COLUMN user_id UUID REFERENCES app_user(id);

-- 2. Assign existing rows to seed user
UPDATE profile           SET user_id = '00000000-0000-0000-0000-000000000001';
UPDATE project           SET user_id = '00000000-0000-0000-0000-000000000001';
UPDATE application       SET user_id = '00000000-0000-0000-0000-000000000001';
UPDATE generation_config SET user_id = '00000000-0000-0000-0000-000000000001';

-- 3. Enforce NOT NULL now that all rows have a value
ALTER TABLE profile           ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE project           ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE application       ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE generation_config ALTER COLUMN user_id SET NOT NULL;

-- 4. Drop singleton constraints and columns (replaced by user_id)
ALTER TABLE profile DROP CONSTRAINT profile_singleton_key;
ALTER TABLE profile DROP COLUMN singleton;

ALTER TABLE generation_config DROP CONSTRAINT generation_config_singleton;
ALTER TABLE generation_config DROP COLUMN singleton;

-- 5. Indexes for per-user queries
CREATE INDEX project_user_id_idx           ON project(user_id);
CREATE INDEX application_user_id_idx       ON application(user_id);
CREATE INDEX profile_user_id_idx           ON profile(user_id);
CREATE INDEX generation_config_user_id_idx ON generation_config(user_id);
