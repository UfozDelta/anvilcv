-- The discipline a user writes for (software | mechanical | other). Picked at onboarding, editable later.
-- Every existing profile stays 'software', so nothing about their generation changes.
ALTER TABLE profile
    ADD COLUMN IF NOT EXISTS field varchar(20) NOT NULL DEFAULT 'software';
