CREATE TABLE clubs (id BIGSERIAL PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    description VARCHAR(255)
);

-- 1. Таблиця тренерів
CREATE TABLE coaches (
                         id BIGSERIAL PRIMARY KEY,
                         name VARCHAR(255) NOT NULL,
                         specialization VARCHAR(100),
                         club_id BIGINT REFERENCES clubs(id) ON DELETE SET NULL
);

-- 2. Таблиця атлетів з описом
CREATE TABLE athletes (
                          id BIGSERIAL PRIMARY KEY,
                          name VARCHAR(255) NOT NULL,
                          description TEXT,
                          coach_id BIGINT REFERENCES coaches(id) ON DELETE SET NULL
);

-- 3. Таблиця тренувань з каскадним видаленням
CREATE TABLE workouts (
                          id BIGSERIAL PRIMARY KEY,
                          title VARCHAR(255) NOT NULL,
                          description TEXT,
                          workout_date DATE NOT NULL,
                          workout_time TIME,
                          duration_minutes INTEGER,
                          athlete_id BIGINT NOT NULL,
                          coach_id BIGINT NOT NULL,
                          CONSTRAINT workouts_athlete_id_fkey
                              FOREIGN KEY (athlete_id)
                                  REFERENCES athletes(id)
                                  ON DELETE CASCADE,
                          CONSTRAINT workouts_coach_id_fkey
                              FOREIGN KEY (coach_id)
                                  REFERENCES coaches(id)
                                  ON DELETE CASCADE
);

-- Команда для існуючої бази (якщо таблиці вже створені):
-- ALTER TABLE athletes ADD COLUMN IF NOT EXISTS description TEXT;
-- ALTER TABLE workouts DROP CONSTRAINT IF EXISTS workouts_athlete_id_fkey;
-- ALTER TABLE workouts ADD CONSTRAINT workouts_athlete_id_fkey FOREIGN KEY (athlete_id) REFERENCES athletes(id) ON DELETE CASCADE;