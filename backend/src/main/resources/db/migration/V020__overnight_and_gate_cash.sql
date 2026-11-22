-- =====================================================================
-- V020: Giờ khóa lấy xe & phí qua đêm (Mục 3.8, 3.9) + thu tiền mặt tại cổng (Mục 3.5, 3.6)
--   1. system_config: thêm checkout_curfew_time (mặc định 20:00), overnight_fee_points (15.000), curfew_enabled
--   2. gate_cash_collections: ghi nhận tiền mặt bảo vệ thu từng lượt xe ra,
--      tách biệt hoàn toàn khỏi ví điểm (R12) và không cho phép sửa/xóa (R10).
-- =====================================================================

ALTER TABLE system_config
    ADD COLUMN checkout_curfew_time TIME    NOT NULL DEFAULT '20:00:00',
    ADD COLUMN overnight_fee_points INT     NOT NULL DEFAULT 15000,
    ADD COLUMN curfew_enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT ck_system_config_overnight CHECK (overnight_fee_points > 0);

CREATE TABLE gate_cash_collections (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id      BIGINT       NOT NULL,
    guard_id        BIGINT       NOT NULL,
    shift_id        BIGINT       NULL,
    amount          BIGINT       NOT NULL,
    overnight       BOOLEAN      NOT NULL DEFAULT FALSE,
    idempotency_key CHAR(36) CHARACTER SET ascii NOT NULL,
    collected_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT uq_gate_cash_session     UNIQUE (session_id),
    CONSTRAINT uq_gate_cash_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_gate_cash_session     FOREIGN KEY (session_id) REFERENCES parking_sessions (id),
    CONSTRAINT fk_gate_cash_guard       FOREIGN KEY (guard_id)   REFERENCES guards (user_id),
    CONSTRAINT fk_gate_cash_shift       FOREIGN KEY (shift_id)   REFERENCES guard_shifts (id),
    CONSTRAINT ck_gate_cash_amount      CHECK (amount > 0)
);

CREATE INDEX ix_gate_cash_guard_time ON gate_cash_collections (guard_id, collected_at DESC);
CREATE INDEX ix_gate_cash_shift      ON gate_cash_collections (shift_id, collected_at DESC);

CREATE TRIGGER trg_gate_cash_no_update BEFORE UPDATE ON gate_cash_collections FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'gate_cash_collections chi cho phep them moi';

CREATE TRIGGER trg_gate_cash_no_delete BEFORE DELETE ON gate_cash_collections FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'gate_cash_collections chi cho phep them moi';
