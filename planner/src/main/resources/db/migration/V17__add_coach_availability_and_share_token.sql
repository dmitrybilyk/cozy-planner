CREATE TABLE mentor_availability (
    id BIGSERIAL PRIMARY KEY,
    mentor_id BIGINT NOT NULL REFERENCES mentors(id),
    date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    place_id BIGINT REFERENCES places(id),
    UNIQUE (mentor_id, date, start_time)
);

ALTER TABLE mentors ADD COLUMN share_token VARCHAR(64);

CREATE UNIQUE INDEX idx_mentors_share_token ON mentors(share_token);
