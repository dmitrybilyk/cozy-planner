CREATE TABLE IF NOT EXISTS user_sessions (
    id TEXT PRIMARY KEY,
    creation_time TIMESTAMP NOT NULL,
    last_access_time TIMESTAMP NOT NULL,
    max_idle_seconds BIGINT NOT NULL,
    attributes TEXT NOT NULL
);
