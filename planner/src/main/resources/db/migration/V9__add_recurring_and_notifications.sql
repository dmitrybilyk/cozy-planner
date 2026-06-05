-- Recurring sessions support
ALTER TABLE meetings ADD COLUMN IF NOT EXISTS recurring BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE meetings ADD COLUMN IF NOT EXISTS recurrence_group_id VARCHAR(36);

-- Mentor: ensure session_reminder_enabled exists (was in V1, here for safety)
-- No-op if column already exists

-- Trainee: bulk availability request tracking (no schema needed — uses existing reminder fields)
