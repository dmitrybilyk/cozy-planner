-- Видалення таблиць у правильному порядку
DROP TABLE IF EXISTS workout_athletes CASCADE;
DROP TABLE IF EXISTS workouts CASCADE;
DROP TABLE IF EXISTS locations CASCADE;
DROP TABLE IF EXISTS athletes CASCADE;
DROP TABLE IF EXISTS coaches CASCADE;
DROP TABLE IF EXISTS clubs CASCADE;

-- 1. Клуби
CREATE TABLE clubs (
                       id BIGSERIAL PRIMARY KEY,
                       name VARCHAR(255) NOT NULL,
                       description TEXT
);

-- 2. Тренери
CREATE TABLE coaches (
                         id BIGSERIAL PRIMARY KEY,
                         name VARCHAR(255) NOT NULL,
                         specialization VARCHAR(255),
                         club_id BIGINT REFERENCES clubs(id) ON DELETE SET NULL
);

-- 3. Атлети
CREATE TABLE athletes (
                          id BIGSERIAL PRIMARY KEY,
                          name VARCHAR(255) NOT NULL,
                          description TEXT,
                          coach_id BIGINT REFERENCES coaches(id) ON DELETE SET NULL
);

-- 4. Локації (Спрощені мітки тренера)
CREATE TABLE locations (
                           id BIGSERIAL PRIMARY KEY,
                           name VARCHAR(255) NOT NULL,
                           description TEXT,
                           color VARCHAR(7) DEFAULT '#3b82f6',
                           coach_id BIGINT NOT NULL REFERENCES coaches(id) ON DELETE CASCADE
);

-- 5. Тренування (Синхронізовано з твоєю Entity Workout)
CREATE TABLE workouts (
                          id BIGSERIAL PRIMARY KEY,
                          title VARCHAR(255) NOT NULL,
                          description TEXT,
                          workout_date DATE NOT NULL,      -- @Column("workout_date")
                          start_time TIME NOT NULL,        -- @Column("start_time")
                          end_time TIME NOT NULL,          -- @Column("end_time")
                          coach_id BIGINT NOT NULL REFERENCES coaches(id) ON DELETE CASCADE,
                          location_id BIGINT REFERENCES locations(id) ON DELETE SET NULL
);

-- 6. Зв'язок Many-to-Many
CREATE TABLE workout_athletes (
                                  workout_id BIGINT NOT NULL REFERENCES workouts(id) ON DELETE CASCADE,
                                  athlete_id BIGINT NOT NULL REFERENCES athletes(id) ON DELETE CASCADE,
                                  PRIMARY KEY (workout_id, athlete_id)
);

-- Індекси
CREATE INDEX idx_workouts_coach_date ON workouts(coach_id, workout_date);
CREATE INDEX idx_locations_coach ON locations(coach_id);