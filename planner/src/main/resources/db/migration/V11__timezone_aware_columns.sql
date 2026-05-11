-- Convert timestamp columns to timezone-aware (TIMESTAMP WITH TIME ZONE)
-- This ensures all datetime values are properly handled with timezone context

-- Trainees (athletes) table
ALTER TABLE trainees ALTER COLUMN telegram_connected_at TYPE TIMESTAMP WITH TIME ZONE;

-- Mentors (coaches) table
ALTER TABLE mentors ALTER COLUMN telegram_connected_at TYPE TIMESTAMP WITH TIME ZONE;

-- Add comment explaining the change
COMMENT ON TABLE trainees IS 'Trainee (athlete) table with timezone-aware datetime columns';
