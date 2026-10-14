-- What each user may use. plan is free | starter | pro | pack; quota_exempt lets an operator lift
-- every cap by hand (UPDATE app_user SET quota_exempt = true WHERE username = '...').
-- plan_started_at starts the clock for a one-off pack, whose tailors do not reset monthly.
ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS plan varchar(20) NOT NULL DEFAULT 'free',
    ADD COLUMN IF NOT EXISTS plan_started_at timestamptz,
    ADD COLUMN IF NOT EXISTS quota_exempt boolean NOT NULL DEFAULT false;

-- Everyone who already has an account keeps using the product uncapped; only new sign-ups are metered.
UPDATE app_user SET quota_exempt = true;

-- One row per tailor (ref_id = application) or generation (ref_id = project) that finished.
-- Counting events, not live rows, so deleting an application does not hand the tailor back.
CREATE TABLE IF NOT EXISTS usage_event (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    kind        varchar(20) NOT NULL,
    ref_id      uuid,
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS usage_event_user_kind_created_idx ON usage_event (user_id, kind, created_at);
