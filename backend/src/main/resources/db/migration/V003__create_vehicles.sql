-- =====================================================================
-- V003: Loại xe và xe của sinh viên (SV02)
-- =====================================================================

CREATE TABLE vehicle_types (
    code VARCHAR(20)  NOT NULL PRIMARY KEY,         -- XE_MAY, XE_DAP, ...
    name VARCHAR(100) NOT NULL
);

CREATE TABLE vehicles (
    id                BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    student_id        BIGINT      NOT NULL,
    plate_number      VARCHAR(20) NOT NULL,        -- đã chuẩn hóa: chữ hoa, bỏ khoảng trắng, '.', '-'
    vehicle_type_code VARCHAR(20) NOT NULL,
    registered_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT uq_vehicles_plate   UNIQUE (plate_number),
    CONSTRAINT ck_vehicles_plate   CHECK (REGEXP_LIKE(plate_number, '^[A-Z0-9]{5,20}$', 'c')),
    CONSTRAINT fk_vehicles_student FOREIGN KEY (student_id)        REFERENCES students (user_id),
    CONSTRAINT fk_vehicles_type    FOREIGN KEY (vehicle_type_code) REFERENCES vehicle_types (code)
);

CREATE INDEX ix_vehicles_student ON vehicles (student_id, registered_at DESC);
