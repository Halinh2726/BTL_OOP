-- V028: Bảng giá gửi xe theo lượt phân theo từng loại xe (cho phép Quản lý chỉnh sửa).
ALTER TABLE duration_fee_rules
    ADD COLUMN vehicle_type_code VARCHAR(20) NOT NULL DEFAULT 'XE_MAY' FIRST;

ALTER TABLE duration_fee_rules
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (vehicle_type_code, up_to_minutes),
    ADD CONSTRAINT fk_duration_fee_vehicle_type
        FOREIGN KEY (vehicle_type_code) REFERENCES vehicle_types (code);

INSERT INTO duration_fee_rules (vehicle_type_code, up_to_minutes, fee_points) VALUES
    ('XE_MAY_DIEN', 120,  2500),
    ('XE_MAY_DIEN', 360,  4000),
    ('XE_MAY_DIEN', 1440, 6000),
    ('XE_DAP',      120,  1500),
    ('XE_DAP',      360,  2500),
    ('XE_DAP',      1440, 4000);
