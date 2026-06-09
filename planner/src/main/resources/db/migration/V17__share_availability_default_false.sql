-- Change share_availability default to FALSE — new mentors start with availability sharing off
ALTER TABLE mentors ALTER COLUMN share_availability SET DEFAULT FALSE;
