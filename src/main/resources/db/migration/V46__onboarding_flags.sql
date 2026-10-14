-- onboarded_at: set when the user finishes or skips the guided first run. NULL means show it.
-- first_download_at: the user's first Download PDF click, the point they got the product's value.
-- Everyone who already has an account is treated as onboarded, so only new sign-ups see the guide.
ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS onboarded_at timestamptz,
    ADD COLUMN IF NOT EXISTS first_download_at timestamptz;

UPDATE app_user SET onboarded_at = created_at WHERE onboarded_at IS NULL;
