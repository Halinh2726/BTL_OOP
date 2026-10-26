-- =====================================================================
-- V009: Dữ liệu danh mục cố định: vai trò, quyền, loại xe, bảng giá
-- =====================================================================

INSERT INTO roles (code, name) VALUES
    ('SINH_VIEN',     'Sinh viên'),
    ('BAO_VE',        'Bảo vệ'),
    ('QUAN_LY',       'Quản lý'),
    ('CHU_TAI_KHOAN', 'Chủ tài khoản');

INSERT INTO permissions (code, description) VALUES
    ('VEHICLE_REGISTER',  'Đăng ký và xem xe của mình'),
    ('WALLET_VIEW_OWN',   'Xem số điểm và lịch sử giao dịch của mình'),
    ('PARKING_CHECK_IN',  'Xác minh xe vào'),
    ('PARKING_CHECK_OUT', 'Xác minh xe ra và kích hoạt thanh toán'),
    ('CASH_DEPOSIT',      'Đổi tiền mặt sang điểm cho sinh viên'),
    ('CASH_WITHDRAWAL',   'Rút điểm ra tiền mặt cho chủ tài khoản'),
    ('GUARD_CREATE',      'Tạo tài khoản bảo vệ'),
    ('GUARD_LOCK',        'Khóa / mở khóa tài khoản bảo vệ');

-- Ma trận quyền theo Đặc tả API mục 4
INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('SINH_VIEN',     'VEHICLE_REGISTER'),
    ('SINH_VIEN',     'WALLET_VIEW_OWN'),
    ('BAO_VE',        'PARKING_CHECK_IN'),
    ('BAO_VE',        'PARKING_CHECK_OUT'),
    ('QUAN_LY',       'CASH_DEPOSIT'),
    ('QUAN_LY',       'CASH_WITHDRAWAL'),
    ('QUAN_LY',       'GUARD_CREATE'),
    ('QUAN_LY',       'GUARD_LOCK'),
    ('CHU_TAI_KHOAN', 'WALLET_VIEW_OWN');

INSERT INTO vehicle_types (code, name) VALUES
    ('XE_MAY',    'Xe máy'),
    ('XE_DAP',    'Xe đạp'),
    ('XE_DAP_DIEN', 'Xe đạp điện');

-- R03: trước 18:00 -> 3.000 điểm; từ 18:00 -> 5.000 điểm. Khung 00:00 bắt buộc phải có.
INSERT INTO fee_rules (start_time, fee_points) VALUES
    ('00:00', 3000),
    ('18:00', 5000);
