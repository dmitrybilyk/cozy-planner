CREATE TABLE clubs (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT
);

CREATE TABLE coaches (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    specialization VARCHAR(255),
    club_id BIGINT REFERENCES clubs(id) ON DELETE SET NULL
);

CREATE TABLE athletes (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    coach_id BIGINT REFERENCES coaches(id) ON DELETE SET NULL
);

CREATE TABLE locations (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    color VARCHAR(7) DEFAULT '#3b82f6',
    coach_id BIGINT NOT NULL REFERENCES coaches(id) ON DELETE CASCADE
);

CREATE TABLE workouts (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    workout_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    coach_id BIGINT NOT NULL REFERENCES coaches(id) ON DELETE CASCADE,
    location_id BIGINT REFERENCES locations(id) ON DELETE SET NULL
);

CREATE TABLE workout_athletes (
    workout_id BIGINT NOT NULL REFERENCES workouts(id) ON DELETE CASCADE,
    athlete_id BIGINT NOT NULL REFERENCES athletes(id) ON DELETE CASCADE,
    PRIMARY KEY (workout_id, athlete_id)
);

CREATE INDEX idx_workouts_coach_date ON workouts(coach_id, workout_date);
CREATE INDEX idx_locations_coach ON locations(coach_id);
