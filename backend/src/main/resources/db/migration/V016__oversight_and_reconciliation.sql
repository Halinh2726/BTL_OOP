-- =====================================================================
-- V016 (Đợt 4a): giám sát & đối soát của quản lý
--   * Quyền mới: xem hồ sơ sinh viên, sổ giao dịch toàn hệ thống, đối soát cuối ngày, thống kê lượt xe theo giờ
--   * daily_reconciliations: biên bản chốt sổ tiền mặt cuối ngày — chỉ THÊM (cùng tinh thần R10)
--   * Chỉ mục phục vụ lọc theo thời gian
-- =====================================================================

INSERT INTO permissions (code, description) VALUES
    ('STUDENT_VIEW',   'Xem hồ sơ sinh viên: thông tin, số điểm, xe, lượt gửi, giao dịch'),
    ('LEDGER_VIEW',    'Xem sổ giao dịch toàn hệ thống, lọc và xuất CSV'),
    ('CASH_RECONCILE', 'Đối soát tiền mặt cuối ngày và chốt biên bản'),
    ('TRAFFIC_STATS',  'Xem thống kê lượt xe vào / ra theo giờ (giờ cao điểm)');

INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('QUAN_LY',       'STUDENT_VIEW'),
    ('QUAN_LY',       'LEDGER_VIEW'),
    ('QUAN_LY',       'CASH_RECONCILE'),
    ('QUAN_LY',       'TRAFFIC_STATS'),
    ('CHU_TAI_KHOAN', 'TRAFFIC_STATS');

-- ---------------------------------------------------------------------
-- Biên bản đối soát: mỗi ngày (giờ Việt Nam) chốt MỘT lần.
--   expected_cash = tiền mặt phải có theo sổ (R13) tại cuối ngày đó
--   counted_cash  = tiền quản lý đếm thực tế
--   Lệch (counted <> expected) thì BẮT BUỘC có giải trình.
-- ---------------------------------------------------------------------
CREATE TABLE daily_reconciliations (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_date   DATE         NOT NULL,
    cash_in         BIGINT       NOT NULL,
    cash_out        BIGINT       NOT NULL,
    expected_cash   BIGINT       NOT NULL,
    counted_cash    BIGINT       NOT NULL,
    points_balanced BOOLEAN      NOT NULL,                 -- R12 tại thời điểm chốt
    note            VARCHAR(500) NULL,
    closed_by       BIGINT       NOT NULL,
    closed_at       DATETIME(6)  NOT NULL,

    CONSTRAINT uq_daily_reconciliations_date UNIQUE (business_date),
    CONSTRAINT fk_daily_reconciliations_user FOREIGN KEY (closed_by) REFERENCES users (id),
    CONSTRAINT ck_daily_reconciliations_cash CHECK (cash_in >= 0 AND cash_out >= 0 AND counted_cash >= 0),
    -- Lệch sổ mà không giải trình -> CSDL cũng chặn (IS NOT NULL tường minh: so sánh với NULL là UNKNOWN)
    CONSTRAINT ck_daily_reconciliations_note CHECK (
        counted_cash = expected_cash OR (note IS NOT NULL AND CHAR_LENGTH(TRIM(note)) > 0)
    )
);

CREATE TRIGGER trg_daily_reconciliations_no_update BEFORE UPDATE ON daily_reconciliations FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'daily_reconciliations chi cho phep them moi';

CREATE TRIGGER trg_daily_reconciliations_no_delete BEFORE DELETE ON daily_reconciliations FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'daily_reconciliations chi cho phep them moi';

-- Sổ giao dịch lọc theo khoảng thời gian; thống kê lượt xe theo giờ vào / giờ ra
CREATE INDEX ix_point_transactions_created ON point_transactions (created_at);
CREATE INDEX ix_parking_sessions_entered   ON parking_sessions (entered_at);
CREATE INDEX ix_parking_sessions_exited    ON parking_sessions (exited_at);
