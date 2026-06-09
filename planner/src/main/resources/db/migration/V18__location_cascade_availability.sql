-- When a location is deleted, automatically remove its availability records
ALTER TABLE mentor_availability DROP CONSTRAINT mentor_availability_place_id_fkey;
ALTER TABLE mentor_availability ADD CONSTRAINT mentor_availability_place_id_fkey
    FOREIGN KEY (place_id) REFERENCES places(id) ON DELETE CASCADE;
