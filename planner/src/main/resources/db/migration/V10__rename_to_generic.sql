-- Rename tables and columns from sport-specific to generic names

-- Rename tables
ALTER TABLE coaches RENAME TO mentors;
ALTER TABLE athletes RENAME TO trainees;
ALTER TABLE locations RENAME TO places;
ALTER TABLE workouts RENAME TO meetings;
ALTER TABLE workout_athletes RENAME TO meeting_trainees;
ALTER TABLE athlete_availability RENAME TO trainee_availability;

-- Rename columns in mentors table (no changes needed, just the table)
-- Columns: id, name, specialization, club_id, telegram_chat_id, telegram_username, telegram_connected_at, telegram_token

-- Rename columns in trainees table
ALTER TABLE trainees RENAME COLUMN coach_id TO mentor_id;
-- Columns now: id, name, description, mentor_id, invite_token, telegram_chat_id, telegram_username, telegram_connected_at, photo_base64, weekend_reminder_enabled

-- Rename columns in places table
ALTER TABLE places RENAME COLUMN coach_id TO mentor_id;
-- Columns now: id, name, description, color, mentor_id

-- Rename columns in meetings table
ALTER TABLE meetings RENAME COLUMN coach_id TO mentor_id;
ALTER TABLE meetings RENAME COLUMN location_id TO place_id;
ALTER TABLE meetings RENAME COLUMN workout_date TO meeting_date;
-- Columns now: id, title, description, meeting_date, start_time, end_time, mentor_id, place_id

-- Rename columns in meeting_trainees join table
ALTER TABLE meeting_trainees RENAME COLUMN workout_id TO meeting_id;
ALTER TABLE meeting_trainees RENAME COLUMN athlete_id TO trainee_id;

-- Rename columns in trainee_availability table
ALTER TABLE trainee_availability RENAME COLUMN athlete_id TO trainee_id;

-- Recreate indexes with new names
DROP INDEX IF EXISTS idx_workouts_coach_date;
CREATE INDEX idx_meetings_mentor_date ON meetings(mentor_id, meeting_date);

DROP INDEX IF EXISTS idx_locations_coach;
CREATE INDEX idx_places_mentor ON places(mentor_id);

DROP INDEX IF EXISTS idx_availability_athlete_date;
CREATE INDEX idx_availability_trainee_date ON trainee_availability(trainee_id, date);
