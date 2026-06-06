CREATE TABLE IF NOT EXISTS session_feedback (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT,
    from_mentor_id BIGINT,
    from_trainee_id BIGINT,
    to_mentor_id BIGINT,
    to_trainee_id BIGINT,
    text TEXT,
    tags VARCHAR(500),
    rating SMALLINT,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_feedback_to_trainee ON session_feedback(to_trainee_id);
CREATE INDEX IF NOT EXISTS idx_feedback_to_mentor ON session_feedback(to_mentor_id);
CREATE INDEX IF NOT EXISTS idx_feedback_from_mentor ON session_feedback(from_mentor_id);
CREATE INDEX IF NOT EXISTS idx_feedback_from_trainee ON session_feedback(from_trainee_id);
