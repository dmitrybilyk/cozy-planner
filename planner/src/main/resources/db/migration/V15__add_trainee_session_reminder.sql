ALTER TABLE trainees ADD COLUMN session_reminder_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE meetings ADD COLUMN trainee_reminder_sent BOOLEAN DEFAULT FALSE;
