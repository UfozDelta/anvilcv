-- Stripe links. customer is set on the first checkout; subscription only for starter/pro (a pack is a
-- one-off payment). plan_started_at already exists (V47) and starts a pack's clock.
ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS stripe_customer_id     varchar(64),
    ADD COLUMN IF NOT EXISTS stripe_subscription_id varchar(64);

CREATE INDEX IF NOT EXISTS app_user_stripe_customer_idx     ON app_user (stripe_customer_id);
CREATE INDEX IF NOT EXISTS app_user_stripe_subscription_idx ON app_user (stripe_subscription_id);

-- Stripe retries a webhook until it gets a 2xx and may deliver one twice. An event id seen here is skipped.
CREATE TABLE IF NOT EXISTS stripe_event (
    id           varchar(80) PRIMARY KEY,
    type         varchar(80) NOT NULL,
    processed_at timestamptz NOT NULL DEFAULT now()
);
