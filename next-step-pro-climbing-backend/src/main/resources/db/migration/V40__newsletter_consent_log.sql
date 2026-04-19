CREATE TABLE newsletter_consent_log (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    action     VARCHAR(20) NOT NULL,
    source     VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_newsletter_consent_log_user_id ON newsletter_consent_log(user_id, created_at DESC);
