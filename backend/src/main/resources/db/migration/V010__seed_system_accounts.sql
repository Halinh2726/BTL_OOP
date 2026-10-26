-- =====================================================================
-- V010: Tài khoản tạo sẵn (đặc tả mục 2): 1 Quản lý, 1 Chủ tài khoản + ví nhận,
--       và system_config trỏ tới ví đó.
-- Mật khẩu demo (BCrypt cost 12) — ĐỔI trước khi dùng thật:
--   quanly01 / Quanly@123
--   chutk01  / Chutk@123
-- =====================================================================

INSERT INTO users (username, password_hash, full_name, role_code) VALUES
    ('quanly01', '$2a$12$ssYEwz3R.7KFsTO1xoVREeetz.19B0hy995jgcyJGB69zwC.4fLgi', 'Phạm Văn Quản', 'QUAN_LY'),
    ('chutk01',  '$2a$12$ASa8VBZsssBaPPVdNOWJueVWXRMXFlIv5cZloWqCrkbMJkorSFEOC', 'Lê Văn C',      'CHU_TAI_KHOAN');

INSERT INTO wallets (user_id, balance)
SELECT id, 0 FROM users WHERE username = 'chutk01';

-- Tỷ lệ 1 đồng = 1 điểm (R02 ví dụ trong đặc tả)
INSERT INTO system_config (id, cash_per_point, receiving_wallet_id)
SELECT 1, 1, w.id
FROM wallets w
JOIN users u ON u.id = w.user_id
WHERE u.username = 'chutk01';
