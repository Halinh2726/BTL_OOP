-- Bảng giá theo thời lượng thực tế. Mỗi chu kỳ tính tối đa 1.440 phút.
CREATE TABLE duration_fee_rules (
    up_to_minutes INT NOT NULL PRIMARY KEY,
    fee_points    INT NOT NULL,
    CONSTRAINT ck_duration_fee_minutes CHECK (up_to_minutes BETWEEN 1 AND 1440),
    CONSTRAINT ck_duration_fee_points CHECK (fee_points > 0)
);

INSERT INTO duration_fee_rules (up_to_minutes, fee_points) VALUES
    (120,  3000),
    (360,  5000),
    (1440, 7000);
