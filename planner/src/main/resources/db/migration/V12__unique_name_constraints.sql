-- Add NOT NULL constraints and UNIQUE indexes
-- Mentor names: NOT NULL but NOT unique (multiple clubs can have mentors with same name)
-- Trainee names: NOT NULL and unique WITHIN THE SAME MENTOR (composite unique)

-- Trainees table: name cannot be null and must be unique per mentor
ALTER TABLE trainees ALTER COLUMN name SET NOT NULL;
DROP INDEX IF EXISTS idx_trainees_unique_name;
CREATE UNIQUE INDEX IF NOT EXISTS idx_trainees_unique_name_per_mentor ON trainees(mentor_id, LOWER(name));

-- Mentors table: name cannot be null but does NOT need to be unique
ALTER TABLE mentors ALTER COLUMN name SET NOT NULL;
DROP INDEX IF EXISTS idx_mentors_unique_name;
