ALTER TABLE athletes ADD COLUMN invite_token VARCHAR(64) UNIQUE;

DROP TABLE IF EXISTS athlete_availability;

CREATE TABLE athlete_availability (
    id BIGSERIAL PRIMARY KEY,
    athlete_id BIGINT NOT NULL REFERENCES athletes(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL
);

CREATE INDEX idx_availability_athlete_date ON athlete_availability(athlete_id, date);
