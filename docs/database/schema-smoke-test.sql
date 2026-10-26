-- Kiểm tra nhanh các ràng buộc nghiệp vụ sau khi chạy migration.
-- Mỗi khối DO thử một thao tác SAI và khẳng định CSDL phải từ chối.
-- Chạy: psql -v ON_ERROR_STOP=1 -f schema-smoke-test.sql

BEGIN;

-- Dữ liệu mẫu: 1 sinh viên + ví 10.000 điểm, 1 bảo vệ, 1 xe
INSERT INTO users (username, password_hash, full_name, role_code) VALUES
    ('sv01',    'x', 'Nguyễn Văn A', 'SINH_VIEN'),
    ('baove01', 'x', 'Trần Văn B',   'BAO_VE');
INSERT INTO students (user_id, student_code) SELECT id, 'B21DCCN001' FROM users WHERE username = 'sv01';
INSERT INTO guards   (user_id, phone)        SELECT id, '0901234567' FROM users WHERE username = 'baove01';
INSERT INTO wallets  (user_id, balance)      SELECT id, 10000        FROM users WHERE username = 'sv01';
INSERT INTO vehicles (student_id, plate_number, vehicle_type_code)
    SELECT id, '29A12345', 'XE_MAY' FROM users WHERE username = 'sv01';
INSERT INTO parking_sessions (vehicle_id, entry_guard_id)
    SELECT v.id, g.user_id FROM vehicles v, guards g;

DO $$
DECLARE
    ok BOOLEAN;
BEGIN
    -- R01: ví không âm
    BEGIN UPDATE wallets SET balance = -1; ok := false;
    EXCEPTION WHEN check_violation THEN ok := true; END;
    ASSERT ok, 'R01 thất bại: ví âm được chấp nhận';

    -- R05: mở lượt gửi thứ 2 cho cùng xe
    BEGIN INSERT INTO parking_sessions (vehicle_id, entry_guard_id) SELECT v.id, g.user_id FROM vehicles v, guards g; ok := false;
    EXCEPTION WHEN unique_violation THEN ok := true; END;
    ASSERT ok, 'R05 thất bại: xe có 2 lượt gửi mở';

    -- Bảo vệ không được có MSSV (FK kép user_id + role_code)
    BEGIN INSERT INTO students (user_id, student_code) SELECT user_id, 'FAKE01' FROM guards; ok := false;
    EXCEPTION WHEN foreign_key_violation THEN ok := true; END;
    ASSERT ok, 'Kế thừa thất bại: bảo vệ có dòng students';

    -- Trùng biển số
    BEGIN INSERT INTO vehicles (student_id, plate_number, vehicle_type_code) SELECT user_id, '29A12345', 'XE_MAY' FROM students; ok := false;
    EXCEPTION WHEN unique_violation THEN ok := true; END;
    ASSERT ok, 'UNIQUE biển số thất bại';

    -- Lượt gửi DA_RA mà thiếu giờ ra
    BEGIN UPDATE parking_sessions SET status = 'DA_RA'; ok := false;
    EXCEPTION WHEN check_violation THEN ok := true; END;
    ASSERT ok, 'ck_parking_sessions_state thất bại';

    -- Giao dịch DOI_DIEM có ví nguồn -> sai hình dạng
    BEGIN
        INSERT INTO point_transactions (type, points, source_wallet_id, target_wallet_id, target_balance_after,
                                        idempotency_key, request_hash, performed_by)
        SELECT 'DOI_DIEM', 100, w.id, w.id, 100, gen_random_uuid(), 'h', w.user_id FROM wallets w LIMIT 1;
        ok := false;
    EXCEPTION WHEN check_violation THEN ok := true; END;
    ASSERT ok, 'ck_point_transactions_shape thất bại';

    RAISE NOTICE 'Các ràng buộc chặn dữ liệu sai: OK';
END $$;

-- R10: giao dịch hợp lệ được thêm, nhưng không được sửa/xóa
INSERT INTO point_transactions (type, points, target_wallet_id, target_balance_after,
                                idempotency_key, request_hash, performed_by)
SELECT 'DOI_DIEM', 10000, w.id, 10000, gen_random_uuid(), 'h', (SELECT id FROM users WHERE username = 'quanly01')
FROM wallets w JOIN users u ON u.id = w.user_id WHERE u.username = 'sv01';

DO $$
DECLARE ok BOOLEAN;
BEGIN
    BEGIN UPDATE point_transactions SET points = 1; ok := false;
    EXCEPTION WHEN restrict_violation THEN ok := true; END;
    ASSERT ok, 'R10 thất bại: sửa được giao dịch';

    BEGIN DELETE FROM point_transactions; ok := false;
    EXCEPTION WHEN restrict_violation THEN ok := true; END;
    ASSERT ok, 'R10 thất bại: xóa được giao dịch';

    RAISE NOTICE 'R10 append-only: OK';
END $$;

SELECT * FROM v_reconciliation;                       -- R12: points_balanced = true
SELECT username, permission_code FROM v_user_permissions ORDER BY 1, 2;

ROLLBACK;   -- không để lại dữ liệu test
