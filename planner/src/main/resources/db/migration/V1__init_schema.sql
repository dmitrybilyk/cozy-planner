CREATE TABLE clubs (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT
);

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    google_sub VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE clubs ADD COLUMN user_id BIGINT REFERENCES users(id);

CREATE TABLE mentors (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    specialization VARCHAR(255),
    club_id BIGINT REFERENCES clubs(id) ON DELETE SET NULL,
    telegram_chat_id TEXT,
    telegram_username TEXT,
    telegram_connected_at TIMESTAMP WITH TIME ZONE,
    telegram_token VARCHAR(64),
    timezone VARCHAR(64) DEFAULT 'Europe/Kiev',
    session_reminder_enabled BOOLEAN DEFAULT TRUE,
    session_reminder_minutes INT DEFAULT 60,
    profile VARCHAR(32) NOT NULL DEFAULT 'sport',
    photo_url TEXT,
    work_start VARCHAR(5) DEFAULT '09:00',
    work_end VARCHAR(5) DEFAULT '21:00',
    notifications_enabled BOOLEAN DEFAULT TRUE,
    avail_step INT NOT NULL DEFAULT 30,
    share_token VARCHAR(64),
    coach_token VARCHAR(255)
);

CREATE UNIQUE INDEX idx_mentors_share_token ON mentors(share_token);
ALTER TABLE mentors ADD CONSTRAINT uk_mentors_telegram_chat_id UNIQUE (telegram_chat_id);
ALTER TABLE mentors ADD CONSTRAINT uk_mentors_coach_token UNIQUE (coach_token);

CREATE TABLE trainees (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    mentor_id BIGINT REFERENCES mentors(id) ON DELETE SET NULL,
    invite_token VARCHAR(64) UNIQUE,
    telegram_chat_id VARCHAR(64),
    telegram_username VARCHAR(255),
    telegram_connected_at TIMESTAMP WITH TIME ZONE,
    photo_base64 TEXT,
    weekend_reminder_enabled BOOLEAN DEFAULT FALSE,
    timezone VARCHAR(64) DEFAULT 'Europe/Kiev',
    session_reminder_enabled BOOLEAN DEFAULT FALSE,
    monday_reminder_enabled BOOLEAN DEFAULT FALSE,
    notifications_enabled BOOLEAN DEFAULT TRUE
);

ALTER TABLE trainees ADD CONSTRAINT uk_trainees_telegram_chat_id UNIQUE (telegram_chat_id);
CREATE UNIQUE INDEX idx_trainees_unique_name_per_mentor ON trainees(mentor_id, LOWER(name));

CREATE TABLE places (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    color VARCHAR(7) DEFAULT '#3b82f6',
    mentor_id BIGINT NOT NULL REFERENCES mentors(id) ON DELETE CASCADE
);

CREATE TABLE meetings (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    meeting_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    mentor_id BIGINT NOT NULL REFERENCES mentors(id) ON DELETE CASCADE,
    place_id BIGINT REFERENCES places(id) ON DELETE SET NULL,
    reminder_sent BOOLEAN DEFAULT FALSE,
    trainee_reminder_sent BOOLEAN DEFAULT FALSE,
    confirmation_status VARCHAR(20) DEFAULT 'NONE',
    created_by VARCHAR(10) DEFAULT 'COACH',
    confirmed_trainee_ids TEXT DEFAULT '',
    rejected_trainee_ids VARCHAR(255) NOT NULL DEFAULT ''
);

CREATE TABLE meeting_trainees (
    meeting_id BIGINT NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    trainee_id BIGINT NOT NULL REFERENCES trainees(id) ON DELETE CASCADE,
    PRIMARY KEY (meeting_id, trainee_id)
);

CREATE INDEX idx_meetings_mentor_date ON meetings(mentor_id, meeting_date);
CREATE INDEX idx_places_mentor ON places(mentor_id);

CREATE TABLE mentor_availability (
    id BIGSERIAL PRIMARY KEY,
    mentor_id BIGINT NOT NULL REFERENCES mentors(id),
    date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    place_id BIGINT REFERENCES places(id)
);
CREATE INDEX idx_mentor_availability_mentor_date ON mentor_availability(mentor_id, date);

CREATE TABLE mentor_day_offs (
    id BIGSERIAL PRIMARY KEY,
    mentor_id BIGINT NOT NULL REFERENCES mentors(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    UNIQUE(mentor_id, date)
);

-- Legacy trainee availability table (use availability_ranges instead)
CREATE TABLE trainee_availability (
    id BIGSERIAL PRIMARY KEY,
    trainee_id BIGINT NOT NULL REFERENCES trainees(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL
);
CREATE INDEX idx_availability_trainee_date ON trainee_availability(trainee_id, date);

CREATE TABLE availability_ranges (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    user_type VARCHAR(10) NOT NULL CHECK (user_type IN ('COACH', 'TRAINEE')),
    date DATE NOT NULL,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    location_id BIGINT REFERENCES places(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_availability_ranges_user ON availability_ranges(user_id, user_type, date);

CREATE TABLE user_sessions (
    id TEXT PRIMARY KEY,
    creation_time TIMESTAMP NOT NULL,
    last_access_time TIMESTAMP NOT NULL,
    max_idle_seconds BIGINT NOT NULL,
    attributes TEXT NOT NULL
);

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    trainee_id BIGINT,
    mentor_id BIGINT,
    title VARCHAR(255) NOT NULL,
    message TEXT,
    type VARCHAR(50) NOT NULL,
    session_id BIGINT,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_notifications_trainee_id ON notifications(trainee_id);
CREATE INDEX idx_notifications_mentor_id ON notifications(mentor_id);
CREATE INDEX idx_notifications_created_at ON notifications(created_at DESC);

CREATE TABLE push_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    trainee_id BIGINT,
    mentor_id BIGINT,
    endpoint TEXT NOT NULL,
    auth_key TEXT NOT NULL,
    p256dh_key TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_push_subs_trainee ON push_subscriptions(trainee_id);
CREATE INDEX idx_push_subs_mentor ON push_subscriptions(mentor_id);

-- Seed data
INSERT INTO users (email, name, google_sub, created_at)
VALUES ('demo@cozyplanner.app', 'Демо', 'demo-seed', CURRENT_TIMESTAMP);

INSERT INTO clubs (name, description, user_id)
VALUES ('Спортивний Hub', 'Демо клуб', (SELECT id FROM users WHERE google_sub = 'demo-seed'));

INSERT INTO mentors (name, specialization, club_id, profile, work_start, work_end, timezone, share_token, coach_token)
VALUES ('Катя', 'Фітнес-тренер', (SELECT id FROM clubs WHERE name = 'Спортивний Hub'), 'sport', '09:00', '21:00', 'Europe/Kiev', 'demo-share', md5('demo-coach'));

INSERT INTO trainees (name, description, mentor_id, timezone)
VALUES
    ('Анна', 'Початківець', (SELECT id FROM mentors WHERE name = 'Катя'), 'Europe/Kiev'),
    ('Марія', 'Досвідчена', (SELECT id FROM mentors WHERE name = 'Катя'), 'Europe/Kiev'),
    ('Олена', 'Середній рівень', (SELECT id FROM mentors WHERE name = 'Катя'), 'Europe/Kiev'),
    ('Наталія', 'Відновлення після травми', (SELECT id FROM mentors WHERE name = 'Катя'), 'Europe/Kiev');

INSERT INTO places (name, description, color, mentor_id)
VALUES
    ('Зал 1', 'Основний тренувальний зал', '#3b82f6', (SELECT id FROM mentors WHERE name = 'Катя')),
    ('Зал 2', 'Малий зал для індивідуальних', '#22c55e', (SELECT id FROM mentors WHERE name = 'Катя'));

INSERT INTO meetings (title, description, meeting_date, start_time, end_time, mentor_id, place_id)
VALUES
    ('Ранкове тренування', 'Базові вправи', CURRENT_DATE, '09:00', '10:00', (SELECT id FROM mentors WHERE name = 'Катя'), (SELECT id FROM places WHERE name = 'Зал 1')),
    ('Розтяжка', 'Групове заняття', CURRENT_DATE, '10:00', '11:00', (SELECT id FROM mentors WHERE name = 'Катя'), (SELECT id FROM places WHERE name = 'Зал 2')),
    ('Силове тренування', 'Індивідуальне', CURRENT_DATE + 1, '14:00', '15:30', (SELECT id FROM mentors WHERE name = 'Катя'), (SELECT id FROM places WHERE name = 'Зал 1'));

INSERT INTO meeting_trainees (meeting_id, trainee_id)
SELECT m.id, t.id FROM meetings m, trainees t WHERE m.title = 'Ранкове тренування' AND t.name = 'Анна';
INSERT INTO meeting_trainees (meeting_id, trainee_id)
SELECT m.id, t.id FROM meetings m, trainees t WHERE m.title = 'Розтяжка' AND t.name = 'Марія';
INSERT INTO meeting_trainees (meeting_id, trainee_id)
SELECT m.id, t.id FROM meetings m, trainees t WHERE m.title = 'Силове тренування' AND t.name = 'Олена';
