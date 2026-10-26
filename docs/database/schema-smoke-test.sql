-- Kiểm tra nhanh các ràng buộc nghiệp vụ sau khi chạy migration (MySQL 8.4).
-- Mỗi lệnh expect_error thử một thao tác SAI; kết quả phải là "OK - bi chan".
-- Chạy (PowerShell, đứng ở thư mục parking-payment-system):
--   Get-Content docs/database/schema-smoke-test.sql | docker exec -i parking-mysql mysql -uparking -pparking parking

DROP PROCEDURE IF EXISTS expect_error;
DELIMITER //
CREATE PROCEDURE expect_error(IN label VARCHAR(100), IN stmt TEXT)
BEGIN
    DECLARE blocked BOOLEAN DEFAULT FALSE;
    DECLARE CONTINUE HANDLER FOR SQLEXCEPTION SET blocked = TRUE;
    SET @sql_text = stmt;
    PREPARE s FROM @sql_text;
    EXECUTE s;
    DEALLOCATE PREPARE s;
    SELECT label AS kiem_tra, IF(blocked, 'OK - bi chan', 'LOI - KHONG bi chan') AS ket_qua;
END//
DELIMITER ;

START TRANSACTION;

-- Dữ liệu mẫu: 1 sinh viên + ví 10.000 điểm, 1 bảo vệ, 1 xe, 1 lượt gửi đang mở
INSERT INTO users (username, password_hash, full_name, role_code) VALUES
    ('sv01',    'x', 'Nguyễn Văn A', 'SINH_VIEN'),
    ('baove01', 'x', 'Trần Văn B',   'BAO_VE');
INSERT INTO students (user_id, student_code) SELECT id, 'B21DCCN001' FROM users WHERE username = 'sv01';
INSERT INTO guards   (user_id, phone)        SELECT id, '0901234567' FROM users WHERE username = 'baove01';
INSERT INTO wallets  (user_id, balance)      SELECT id, 10000        FROM users WHERE username = 'sv01';
INSERT INTO vehicles (student_id, plate_number, vehicle_type_code)
    SELECT user_id, '29A12345', 'XE_MAY' FROM students;
INSERT INTO parking_sessions (vehicle_id, entry_guard_id)
    SELECT v.id, g.user_id FROM vehicles v, guards g;

CALL expect_error('R01: vi am',
    "UPDATE wallets SET balance = -1");
CALL expect_error('R05: 2 luot gui mo cho cung xe',
    "INSERT INTO parking_sessions (vehicle_id, entry_guard_id) SELECT v.id, g.user_id FROM vehicles v, guards g");
CALL expect_error('Ke thua: bao ve co MSSV',
    "INSERT INTO students (user_id, student_code) SELECT user_id, 'FAKE01' FROM guards");
CALL expect_error('UNIQUE bien so',
    "INSERT INTO vehicles (student_id, plate_number, vehicle_type_code) SELECT user_id, '29A12345', 'XE_MAY' FROM students");
CALL expect_error('Username viet hoa',
    "INSERT INTO users (username, password_hash, full_name, role_code) VALUES ('SV02', 'x', 'A', 'SINH_VIEN')");
CALL expect_error('Luot gui DA_RA thieu gio ra',
    "UPDATE parking_sessions SET status = 'DA_RA'");
CALL expect_error('DOI_DIEM co vi nguon (sai hinh dang)',
    "INSERT INTO point_transactions (type, points, source_wallet_id, target_wallet_id, target_balance_after, idempotency_key, request_hash, performed_by) SELECT 'DOI_DIEM', 100, w.id, w.id, 100, UUID(), 'h', w.user_id FROM wallets w LIMIT 1");

-- R05 chiều ngược lại: đóng lượt cũ đúng cách thì xe được gửi lượt mới
UPDATE parking_sessions
SET status = 'DA_RA', exited_at = CURRENT_TIMESTAMP(6), exit_guard_id = (SELECT user_id FROM guards LIMIT 1);
INSERT INTO parking_sessions (vehicle_id, entry_guard_id) SELECT v.id, g.user_id FROM vehicles v, guards g;
SELECT 'R05: dong luot cu roi mo luot moi' AS kiem_tra,
       IF(COUNT(*) = 2, 'OK - cho phep', 'LOI') AS ket_qua
FROM parking_sessions;

-- R10: giao dịch hợp lệ được thêm, nhưng không được sửa/xóa
INSERT INTO point_transactions (type, points, target_wallet_id, target_balance_after,
                                idempotency_key, request_hash, performed_by)
SELECT 'DOI_DIEM', 10000, w.id, 10000, UUID(), 'h', (SELECT id FROM users WHERE username = 'quanly01')
FROM wallets w JOIN users u ON u.id = w.user_id WHERE u.username = 'sv01';

CALL expect_error('R10: sua giao dich', "UPDATE point_transactions SET points = 1");
CALL expect_error('R10: xoa giao dich', "DELETE FROM point_transactions");

SELECT * FROM v_reconciliation;                                        -- R12: points_balanced = 1
SELECT username, permission_code FROM v_user_permissions ORDER BY 1, 2;

ROLLBACK;   -- không để lại dữ liệu test
DROP PROCEDURE expect_error;
