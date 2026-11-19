-- =====================================================================
-- V012 (Đợt 2 — bảo vệ):
--   1. guard_shifts : ca trực + biên bản bàn giao ca. Kết thúc ca thì "chụp" số liệu (số lượt vào/ra, xe còn
--                     trong bãi, sự cố chưa xử lý); bảo vệ ca sau bấm "Nhận ca" để xác nhận.
--                     R06: biên bản chỉ đếm số lượt, KHÔNG có cột điểm hay tiền.
--   2. incidents    : bảo vệ báo sự cố (xe gửi quá lâu, xe chưa đăng ký, xe hư hỏng...) cho quản lý xử lý.
--   3. incident_photos : ảnh chụp đính kèm (tách bảng để danh sách sự cố không phải đọc dữ liệu ảnh).
--   4. Quyền INCIDENT_REPORT cho bảo vệ; chỉ mục tra lượt gửi theo bảo vệ (lịch sử ca trực).
-- =====================================================================

-- ---------- 1. Ca trực ----------
CREATE TABLE guard_shifts (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    guard_id        BIGINT       NOT NULL,
    started_at      DATETIME(6)  NOT NULL,
    ended_at        DATETIME(6)  NULL,
    check_ins       INT          NULL,                 -- số liệu chụp lúc kết thúc ca
    check_outs      INT          NULL,
    vehicles_in_lot INT          NULL,
    open_incidents  INT          NULL,
    handover_note   VARCHAR(500) NULL,
    received_by     BIGINT       NULL,                 -- bảo vệ ca sau đã xác nhận nhận ca
    received_at     DATETIME(6)  NULL,
    -- Mỗi bảo vệ tối đa MỘT ca đang trực (cùng kỹ thuật open_vehicle_id của V005)
    open_guard_id   BIGINT GENERATED ALWAYS AS (CASE WHEN ended_at IS NULL THEN guard_id END) STORED,

    CONSTRAINT uq_guard_shifts_open      UNIQUE (open_guard_id),
    CONSTRAINT fk_guard_shifts_guard     FOREIGN KEY (guard_id)    REFERENCES guards (user_id),
    CONSTRAINT fk_guard_shifts_receiver  FOREIGN KEY (received_by) REFERENCES guards (user_id),
    -- Đang trực: chưa có số liệu; đã kết thúc: đủ số liệu (không âm)
    CONSTRAINT ck_guard_shifts_summary CHECK (
        (ended_at IS NULL AND check_ins IS NULL AND check_outs IS NULL
            AND vehicles_in_lot IS NULL AND open_incidents IS NULL)
     OR (ended_at IS NOT NULL AND check_ins >= 0 AND check_outs >= 0
            AND vehicles_in_lot >= 0 AND open_incidents >= 0)
    ),
    CONSTRAINT ck_guard_shifts_time     CHECK (ended_at IS NULL OR ended_at >= started_at),
    CONSTRAINT ck_guard_shifts_received CHECK (
        (received_by IS NULL AND received_at IS NULL)
     OR (received_by IS NOT NULL AND received_at IS NOT NULL AND ended_at IS NOT NULL AND received_by <> guard_id)
    )
);

CREATE INDEX ix_guard_shifts_pending ON guard_shifts (received_by, ended_at DESC);

-- ---------- 2, 3. Sự cố ----------
CREATE TABLE incidents (
    id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    reported_by  BIGINT        NOT NULL,
    type         VARCHAR(30)   NOT NULL,
    plate_number VARCHAR(20)   NULL,                   -- đã chuẩn hóa; không bắt buộc (sự cố "khác")
    description  VARCHAR(1000) NOT NULL,
    status       VARCHAR(20)   NOT NULL DEFAULT 'MOI',
    created_at   DATETIME(6)   NOT NULL,
    handled_by   BIGINT        NULL,                   -- quản lý nhận xử lý (Đợt 4)
    handled_at   DATETIME(6)   NULL,
    resolution   VARCHAR(1000) NULL,

    CONSTRAINT fk_incidents_reporter FOREIGN KEY (reported_by) REFERENCES users (id),
    CONSTRAINT fk_incidents_handler  FOREIGN KEY (handled_by)  REFERENCES users (id),
    CONSTRAINT ck_incidents_type     CHECK (type IN ('XE_GUI_QUA_LAU', 'XE_CHUA_DANG_KY', 'XE_HU_HONG', 'KHAC')),
    CONSTRAINT ck_incidents_status   CHECK (status IN ('MOI', 'DANG_XU_LY', 'DA_XU_LY', 'TU_CHOI')),
    CONSTRAINT ck_incidents_plate    CHECK (plate_number IS NULL OR REGEXP_LIKE(plate_number, '^[A-Z0-9]{5,20}$', 'c')),
    CONSTRAINT ck_incidents_text     CHECK (CHAR_LENGTH(TRIM(description)) BETWEEN 1 AND 1000)
);

CREATE INDEX ix_incidents_reporter ON incidents (reported_by, id DESC);
CREATE INDEX ix_incidents_status   ON incidents (status, id DESC);

CREATE TABLE incident_photos (
    incident_id  BIGINT       NOT NULL PRIMARY KEY,
    content_type VARCHAR(30)  NOT NULL,
    data         MEDIUMBLOB   NOT NULL,                -- tối đa 2 MB (IncidentPhoto kiểm tra)

    CONSTRAINT fk_incident_photos_incident FOREIGN KEY (incident_id) REFERENCES incidents (id),
    CONSTRAINT ck_incident_photos_type     CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp'))
);

-- ---------- 4. Quyền & chỉ mục ----------
INSERT INTO permissions (code, description) VALUES
    ('INCIDENT_REPORT', 'Báo sự cố tại bãi xe và xem các sự cố mình đã báo');

INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('BAO_VE', 'INCIDENT_REPORT');

CREATE INDEX ix_parking_sessions_entry_guard ON parking_sessions (entry_guard_id, entered_at);
CREATE INDEX ix_parking_sessions_exit_guard  ON parking_sessions (exit_guard_id, exited_at);
