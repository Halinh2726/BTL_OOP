-- V032: Chuyển bảng giá gửi lượt sang 3 khung giờ cố định theo từng loại xe:
--   1080 (trước 18:00): 3.000 điểm
--   1320 (từ 18:00 đến 22:00): 5.000 điểm
--   1440 (qua đêm / sau 22:00, cộng dồn mỗi đêm): 15.000 điểm
DELETE FROM duration_fee_rules;

INSERT INTO duration_fee_rules (customer_type, vehicle_type_code, up_to_minutes, fee_points)
SELECT 'SINH_VIEN', code, 1080, 3000 FROM vehicle_types
UNION ALL
SELECT 'SINH_VIEN', code, 1320, 5000 FROM vehicle_types
UNION ALL
SELECT 'SINH_VIEN', code, 1440, 15000 FROM vehicle_types;
