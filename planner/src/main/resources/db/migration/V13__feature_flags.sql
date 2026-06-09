-- Feature flags for mentor: share availability with trainees, and multi-location support
-- DEFAULT TRUE preserves existing behavior for current users; new mentors get FALSE via entity builder
ALTER TABLE mentors ADD COLUMN IF NOT EXISTS share_availability BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE mentors ADD COLUMN IF NOT EXISTS multi_location BOOLEAN NOT NULL DEFAULT TRUE;
