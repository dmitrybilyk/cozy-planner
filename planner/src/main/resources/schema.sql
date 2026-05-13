-- Видалення таблиць у правильному порядку
DROP TABLE IF EXISTS session_trainees CASCADE;
DROP TABLE IF EXISTS sessions CASCADE;
DROP TABLE IF EXISTS locations CASCADE;
DROP TABLE IF EXISTS trainees CASCADE;
DROP TABLE IF EXISTS mentors CASCADE;
DROP TABLE IF EXISTS clubs CASCADE;

-- 1. Клуби
CREATE TABLE clubs (
                        id BIGSERIAL PRIMARY KEY,
                        name VARCHAR(255) NOT NULL,
                        description TEXT
);

-- 2. Ментори
CREATE TABLE mentors (
                          id BIGSERIAL PRIMARY KEY,
                          name VARCHAR(255) NOT NULL,
                          specialization VARCHAR(255),
                          club_id BIGINT REFERENCES clubs(id) ON DELETE SET NULL
);

-- 3. Треновані
CREATE TABLE trainees (
                           id BIGSERIAL PRIMARY KEY,
                           name VARCHAR(255) NOT NULL,
                           description TEXT,
                           mentor_id BIGINT REFERENCES mentors(id) ON DELETE SET NULL
);

-- 4. Локації (Спрощені мітки ментора)
CREATE TABLE locations (
                            id BIGSERIAL PRIMARY KEY,
                            name VARCHAR(255) NOT NULL,
                            description TEXT,
                            color VARCHAR(7) DEFAULT '#3b82f6',
                            mentor_id BIGINT NOT NULL REFERENCES mentors(id) ON DELETE CASCADE
);

-- 5. Сесії
CREATE TABLE sessions (
                           id BIGSERIAL PRIMARY KEY,
                           title VARCHAR(255) NOT NULL,
                           description TEXT,
                           session_date DATE NOT NULL,
                           start_time TIME NOT NULL,
                           end_time TIME NOT NULL,
                           mentor_id BIGINT NOT NULL REFERENCES mentors(id) ON DELETE CASCADE,
                           location_id BIGINT REFERENCES locations(id) ON DELETE SET NULL
);

-- 6. Зв'язок Many-to-Many
CREATE TABLE session_trainees (
                                   session_id BIGINT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
                                   trainee_id BIGINT NOT NULL REFERENCES trainees(id) ON DELETE CASCADE,
                                   PRIMARY KEY (session_id, trainee_id)
);

-- Індекси
CREATE INDEX idx_sessions_mentor_date ON sessions(mentor_id, session_date);
CREATE INDEX idx_locations_mentor ON locations(mentor_id);
