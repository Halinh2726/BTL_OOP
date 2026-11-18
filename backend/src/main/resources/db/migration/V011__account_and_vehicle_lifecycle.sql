-- =====================================================================
-- V011 (Đợt 1 — tài khoản & xe):
--   1. Thông tin liên hệ: users.email (mọi vai trò), students.phone
--   2. Mật khẩu: cờ bắt buộc đổi (mật khẩu tạm do quản lý cấp) + thời điểm đổi gần nhất
--      (phiên đăng nhập TRƯỚC thời điểm này bị hủy — "đổi mật khẩu thì đăng xuất các phiên khác")
--   3. password_reset_codes: mã OTP 6 số quên mật khẩu (lưu dạng băm, hết hạn 10 phút, sai tối đa 5 lần)
--   4. audit_logs: nhật ký thao tác trên tài khoản (ai, làm gì, với ai, lúc nào) — chỉ THÊM, như R10
--   5. vehicles: "xóa xe" = NGUNG_SU_DUNG (giữ lịch sử lượt gửi); biển số chỉ duy nhất trong các xe ĐANG DÙNG
--   6. vehicle_code_sequence: mã XD-xxxxx bãi cấp cho xe đạp không có biển
--   7. Quyền ACCOUNT_MANAGE cho quản lý; đổi tên hiển thị vai trò CHU_TAI_KHOAN thành "Chủ bãi xe"
-- =====================================================================

-- ---------- 1, 2. Người dùng ----------
ALTER TABLE users
    ADD COLUMN email                VARCHAR(100) NULL AFTER full_name,
    ADD COLUMN must_change_password BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN password_changed_at  DATETIME(6)  NULL,
    ADD CONSTRAINT uq_users_email UNIQUE (email),
    ADD CONSTRAINT ck_users_email CHECK (email IS NULL OR REGEXP_LIKE(email, '^[^@ ]+@[^@ ]+[.][^@ ]+$'));

ALTER TABLE students
    ADD COLUMN phone VARCHAR(11) NULL,
    ADD CONSTRAINT ck_students_phone CHECK (phone IS NULL OR REGEXP_LIKE(phone, '^0[0-9]{9,10}$'));

-- ---------- 3. Mã khôi phục mật khẩu ----------
CREATE TABLE password_reset_codes (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    code_hash       VARCHAR(100) NOT NULL,             -- BCrypt của mã 6 số, không lưu mã rõ
    expires_at      DATETIME(6)  NOT NULL,
    failed_attempts INT          NOT NULL DEFAULT 0,
    used_at         DATETIME(6)  NULL,
    created_at      DATETIME(6)  NOT NULL,

    CONSTRAINT fk_reset_codes_user     FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_reset_codes_attempts CHECK (failed_attempts BETWEEN 0 AND 5),
    CONSTRAINT ck_reset_codes_time     CHECK (expires_at > created_at)
);

CREATE INDEX ix_reset_codes_user ON password_reset_codes (user_id, id DESC);

-- ---------- 4. Nhật ký thao tác ----------
CREATE TABLE audit_logs (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_id       BIGINT       NOT NULL,              -- người thực hiện
    action         VARCHAR(40)  NOT NULL,              -- AuditAction trong Java
    target_user_id BIGINT       NULL,                  -- tài khoản bị tác động (nếu có)
    detail         VARCHAR(500) NOT NULL,
    created_at     DATETIME(6)  NOT NULL,

    CONSTRAINT fk_audit_logs_actor  FOREIGN KEY (actor_id)       REFERENCES users (id),
    CONSTRAINT fk_audit_logs_target FOREIGN KEY (target_user_id) REFERENCES users (id)
);

CREATE INDEX ix_audit_logs_target ON audit_logs (target_user_id, id DESC);

CREATE TRIGGER trg_audit_logs_no_update BEFORE UPDATE ON audit_logs FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_logs chi cho phep them moi';

CREATE TRIGGER trg_audit_logs_no_delete BEFORE DELETE ON audit_logs FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_logs chi cho phep them moi';

-- ---------- 5. Vòng đời xe ----------
-- active_plate = biển số khi xe ĐANG DÙNG, NULL khi đã ngừng (cùng kỹ thuật open_vehicle_id của V005):
-- UNIQUE cho phép nhiều NULL -> xe đã ngừng không giữ chỗ biển số, người mua lại xe vẫn đăng ký được.
ALTER TABLE vehicles
    ADD COLUMN status         VARCHAR(20) NOT NULL DEFAULT 'DANG_DUNG',
    ADD COLUMN deactivated_at DATETIME(6) NULL,
    ADD COLUMN active_plate   VARCHAR(20) GENERATED ALWAYS AS (CASE WHEN status = 'DANG_DUNG' THEN plate_number END) STORED,
    ADD CONSTRAINT ck_vehicles_status CHECK (status IN ('DANG_DUNG', 'NGUNG_SU_DUNG')),
    ADD CONSTRAINT ck_vehicles_state  CHECK (
        (status = 'DANG_DUNG'     AND deactivated_at IS NULL) OR
        (status = 'NGUNG_SU_DUNG' AND deactivated_at IS NOT NULL)
    );

ALTER TABLE vehicles DROP INDEX uq_vehicles_plate;
ALTER TABLE vehicles ADD CONSTRAINT uq_vehicles_active_plate UNIQUE (active_plate);
CREATE INDEX ix_vehicles_plate ON vehicles (plate_number);

-- ---------- 6. Mã xe đạp do bãi cấp ----------
CREATE TABLE vehicle_code_sequence (
    id         SMALLINT NOT NULL DEFAULT 1 PRIMARY KEY,
    next_value INT      NOT NULL,

    CONSTRAINT ck_vehicle_code_sequence_one_row CHECK (id = 1),
    CONSTRAINT ck_vehicle_code_sequence_range   CHECK (next_value BETWEEN 1 AND 100000)
);

INSERT INTO vehicle_code_sequence (id, next_value) VALUES (1, 1);

-- ---------- 7. Quyền & tên vai trò ----------
INSERT INTO permissions (code, description) VALUES
    ('ACCOUNT_MANAGE', 'Cấp mật khẩu tạm, cập nhật thông tin bảo vệ, xem nhật ký thao tác');

INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('QUAN_LY', 'ACCOUNT_MANAGE');

UPDATE roles SET name = 'Chủ bãi xe' WHERE code = 'CHU_TAI_KHOAN';
