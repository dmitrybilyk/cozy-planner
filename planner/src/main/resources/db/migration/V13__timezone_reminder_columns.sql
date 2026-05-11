-- Add timezone and session reminder columns

-- Mentors table: add timezone and session reminder preferences
ALTER TABLE mentors ADD COLUMN IF NOT EXISTS timezone VARCHAR(64) DEFAULT 'Europe/Kiev';
ALTER TABLE mentors ADD COLUMN IF NOT EXISTS session_reminder_enabled BOOLEAN DEFAULT TRUE;
ALTER TABLE mentors ADD COLUMN IF NOT EXISTS session_reminder_minutes INT DEFAULT 60;

-- Trainees table: add timezone
ALTER TABLE trainees ADD COLUMN IF NOT EXISTS timezone VARCHAR(64) DEFAULT 'Europe/Kiev';

-- Meetings table: add reminder_sent flag to prevent duplicate notifications
ALTER TABLE meetings ADD COLUMN IF NOT EXISTS reminder_sent BOOLEAN DEFAULT FALSE;
