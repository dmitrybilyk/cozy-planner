CREATE TABLE IF NOT EXISTS push_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    trainee_id BIGINT,
    mentor_id BIGINT,
    endpoint TEXT NOT NULL,
    auth_key TEXT NOT NULL,
    p256dh_key TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_push_subs_trainee ON push_subscriptions(trainee_id);
CREATE INDEX IF NOT EXISTS idx_push_subs_mentor ON push_subscriptions(mentor_id);
