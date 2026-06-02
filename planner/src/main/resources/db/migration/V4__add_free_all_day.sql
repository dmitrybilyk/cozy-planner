ALTER TABLE availability_ranges ALTER COLUMN start_time DROP NOT NULL;
ALTER TABLE availability_ranges ALTER COLUMN end_time DROP NOT NULL;
ALTER TABLE availability_ranges ADD COLUMN free_all_day BOOLEAN NOT NULL DEFAULT FALSE;
