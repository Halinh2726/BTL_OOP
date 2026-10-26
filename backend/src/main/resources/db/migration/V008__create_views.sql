-- =====================================================================
-- V008: View
--   v_user_permissions : nạp danh sách quyền vào phiên khi đăng nhập
--   v_reconciliation   : đối soát R12 (bảo toàn điểm) và R13 (tiền mặt quản lý giữ)
-- =====================================================================

CREATE VIEW v_user_permissions AS
SELECT u.id AS user_id,
       u.username,
       u.role_code,
       rp.permission_code
FROM users u
JOIN role_permissions rp ON rp.role_code = u.role_code;

CREATE VIEW v_reconciliation AS
WITH totals AS (
    SELECT
        COALESCE(SUM(CASE WHEN t.type = 'DOI_DIEM' THEN t.points      END), 0) AS points_deposited,
        COALESCE(SUM(CASE WHEN t.type = 'RUT_DIEM' THEN t.points      END), 0) AS points_withdrawn,
        COALESCE(SUM(CASE WHEN t.type = 'DOI_DIEM' THEN c.cash_amount END), 0) AS cash_received,
        COALESCE(SUM(CASE WHEN t.type = 'RUT_DIEM' THEN c.cash_amount END), 0) AS cash_paid_out
    FROM point_transactions t
    LEFT JOIN cash_details c ON c.transaction_id = t.id
)
SELECT (SELECT COALESCE(SUM(balance), 0) FROM wallets)                    AS points_in_wallets,
       t.points_deposited,
       t.points_withdrawn,
       (SELECT COALESCE(SUM(balance), 0) FROM wallets)
           = t.points_deposited - t.points_withdrawn                      AS points_balanced,     -- R12 (1 = đúng)
       t.cash_received - t.cash_paid_out                                  AS cash_held_by_manager -- R13
FROM totals t;
