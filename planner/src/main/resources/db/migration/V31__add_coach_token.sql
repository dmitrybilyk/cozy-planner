ALTER TABLE mentors ADD COLUMN IF NOT EXISTS coach_token VARCHAR(255);

UPDATE mentors SET coach_token = md5(random()::text || clock_timestamp()::text || id::text) WHERE coach_token IS NULL;

ALTER TABLE mentors ADD CONSTRAINT uk_mentors_coach_token UNIQUE (coach_token);
