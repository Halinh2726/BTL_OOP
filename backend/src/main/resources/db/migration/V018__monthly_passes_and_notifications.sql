-- =====================================================================
-- V018 (Đợt 5): vé tháng & thông báo
--   1. Loại giao dịch thứ 5 MUA_VE: sinh viên trả điểm cho chủ bãi xe để mua vé tháng — là phép CHUYỂN giữa hai ví
--      nên R12 (bảo toàn điểm) vẫn đúng, view v_reconciliation không phải sửa.
--   2. pass_prices    : giá vé tháng theo loại xe (DỮ LIỆU, không hóa cứng trong code — giống fee_rules).
--      monthly_passes : mỗi xe tối đa MỘT vé cho mỗi tháng; xe có vé thì xe ra không bị trừ phí.
--   3. notifications  : thông báo cho người dùng (mẫu Observer — use case phát sự kiện, bộ lắng nghe ghi thông báo).
--   4. Quyền PASS_BUY cho sinh viên.
-- =====================================================================

-- ---------- 1. Giao dịch mua vé ----------
ALTER TABLE point_transactions DROP CHECK ck_point_transactions_type;
ALTER TABLE point_transactions DROP CHECK ck_point_transactions_shape;

ALTER TABLE point_transactions
    ADD CONSTRAINT ck_point_transactions_type
        CHECK (type IN ('DOI_DIEM', 'THANH_TOAN', 'RUT_DIEM', 'DIEU_CHINH', 'MUA_VE')),
    ADD CONSTRAINT ck_point_transactions_shape CHECK (
        (type = 'DOI_DIEM'
            AND source_wallet_id IS NULL     AND target_wallet_id IS NOT NULL
            AND source_balance_after IS NULL AND target_balance_after IS NOT NULL)
     OR (type = 'RUT_DIEM'
            AND source_wallet_id IS NOT NULL AND target_wallet_id IS NULL
            AND source_balance_after IS NOT NULL AND target_balance_after IS NULL)
     OR (type IN ('THANH_TOAN', 'DIEU_CHINH', 'MUA_VE')
            AND source_wallet_id IS NOT NULL AND target_wallet_id IS NOT NULL
            AND source_wallet_id <> target_wallet_id
            AND source_balance_after IS NOT NULL AND target_balance_after IS NOT NULL)
    );

-- ---------- 2. Vé tháng ----------
CREATE TABLE pass_prices (
    vehicle_type_code VARCHAR(20) NOT NULL PRIMARY KEY,
    monthly_points    BIGINT      NOT NULL,

    CONSTRAINT fk_pass_prices_type   FOREIGN KEY (vehicle_type_code) REFERENCES vehicle_types (code),
    CONSTRAINT ck_pass_prices_points CHECK (monthly_points > 0)
);

-- Khoảng 2 lượt / ngày học × 22 ngày, giảm ~25% so với trả theo lượt
INSERT INTO pass_prices (vehicle_type_code, monthly_points) VALUES
    ('XE_MAY',      100000),
    ('XE_DAP_DIEN',  80000),
    ('XE_DAP',       50000);

CREATE TABLE monthly_passes (
    id             BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    student_id     BIGINT      NOT NULL,
    vehicle_id     BIGINT      NOT NULL,
    valid_month    DATE        NOT NULL,             -- ngày 1 của tháng có hiệu lực (giờ Việt Nam)
    price_points   BIGINT      NOT NULL,             -- giá TẠI THỜI ĐIỂM mua (đổi bảng giá không ảnh hưởng vé cũ)
    transaction_id BIGINT      NOT NULL,
    purchased_at   DATETIME(6) NOT NULL,

    -- Mỗi xe một vé mỗi tháng: hai request mua song song thì request sau bị chặn
    CONSTRAINT uq_monthly_passes_vehicle_month UNIQUE (vehicle_id, valid_month),
    CONSTRAINT uq_monthly_passes_transaction   UNIQUE (transaction_id),
    CONSTRAINT fk_monthly_passes_student       FOREIGN KEY (student_id)     REFERENCES students (user_id),
    CONSTRAINT fk_monthly_passes_vehicle       FOREIGN KEY (vehicle_id)     REFERENCES vehicles (id),
    CONSTRAINT fk_monthly_passes_transaction   FOREIGN KEY (transaction_id) REFERENCES point_transactions (id),
    CONSTRAINT ck_monthly_passes_month         CHECK (DAY(valid_month) = 1),
    CONSTRAINT ck_monthly_passes_price         CHECK (price_points > 0)
);

CREATE INDEX ix_monthly_passes_student ON monthly_passes (student_id, valid_month DESC);

INSERT INTO permissions (code, description) VALUES
    ('PASS_BUY', 'Sinh viên mua vé tháng cho xe của mình bằng điểm');

INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('SINH_VIEN', 'PASS_BUY');

-- ---------- 3. Thông báo ----------
CREATE TABLE notifications (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    kind       VARCHAR(40)  NOT NULL,                -- loại sự kiện (để giao diện chọn biểu tượng)
    title      VARCHAR(150) NOT NULL,
    body       VARCHAR(500) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    read_at    DATETIME(6)  NULL,

    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX ix_notifications_user ON notifications (user_id, id DESC);
