ALTER TABLE vehicles
    DROP CHECK ck_vehicles_plate;

ALTER TABLE vehicles
    ADD CONSTRAINT ck_vehicles_plate
        CHECK (REGEXP_LIKE(plate_number, '^[A-Z0-9Đ]{5,20}$', 'c'));
