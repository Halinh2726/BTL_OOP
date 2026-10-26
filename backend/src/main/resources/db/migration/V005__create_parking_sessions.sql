-- =====================================================================
-- V005: Lượt gửi xe (LuotGui) — BV01 tạo, BV02/HT01 đóng.
-- R05: mỗi xe tối đa MỘT lượt gửi đang mở.
-- MySQL không có "unique index có điều kiện" (partial index) nên dùng cột sinh tự động:
--   open_vehicle_id = vehicle_id khi DANG_GUI, = NULL khi DA_RA.
--   UNIQUE cho phép nhiều NULL -> chỉ chặn trùng các lượt ĐANG MỞ.
-- Cột open_vehicle_id do CSDL tự tính: tầng Java KHÔNG ghi vào cột này.
-- =====================================================================

CREATE TABLE parking_sessions (
    id              BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    vehicle_id      BIGINT      NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'DANG_GUI',
    entered_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    entry_guard_id  BIGINT      NOT NULL,
    exited_at       DATETIME(6) NULL,
    exit_guard_id   BIGINT      NULL,
    open_vehicle_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'DANG_GUI' THEN vehicle_id END) STORED,

    -- R05: hai request check-in song song cho cùng xe -> request sau vi phạm ràng buộc này
    CONSTRAINT uq_parking_sessions_open        UNIQUE (open_vehicle_id),
    CONSTRAINT fk_parking_sessions_vehicle     FOREIGN KEY (vehicle_id)     REFERENCES vehicles (id),
    CONSTRAINT fk_parking_sessions_entry_guard FOREIGN KEY (entry_guard_id) REFERENCES guards (user_id),
    CONSTRAINT fk_parking_sessions_exit_guard  FOREIGN KEY (exit_guard_id)  REFERENCES guards (user_id),
    CONSTRAINT ck_parking_sessions_status      CHECK (status IN ('DANG_GUI', 'DA_RA')),
    -- Trạng thái và dữ liệu "ra" phải nhất quán: không thể có DA_RA mà thiếu giờ ra / bảo vệ ra
    CONSTRAINT ck_parking_sessions_state CHECK (
        (status = 'DANG_GUI' AND exited_at IS NULL     AND exit_guard_id IS NULL) OR
        (status = 'DA_RA'    AND exited_at IS NOT NULL AND exit_guard_id IS NOT NULL)
    ),
    CONSTRAINT ck_parking_sessions_time CHECK (exited_at IS NULL OR exited_at >= entered_at)
);

CREATE INDEX ix_parking_sessions_vehicle ON parking_sessions (vehicle_id, entered_at DESC);
