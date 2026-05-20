CREATE TABLE mentor_day_offs (
    id BIGSERIAL PRIMARY KEY,
    mentor_id BIGINT NOT NULL REFERENCES mentors(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    UNIQUE(mentor_id, date)
);
