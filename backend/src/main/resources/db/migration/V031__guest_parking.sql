CREATE INDEX ix_duration_fee_vehicle_type ON duration_fee_rules (vehicle_type_code);

ALTER TABLE duration_fee_rules
    ADD COLUMN customer_type VARCHAR(20) NOT NULL DEFAULT 'SINH_VIEN' FIRST,
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (customer_type, vehicle_type_code, up_to_minutes),
    ADD CONSTRAINT ck_duration_fee_customer CHECK (customer_type IN ('SINH_VIEN', 'KHACH'));

INSERT INTO duration_fee_rules (customer_type, vehicle_type_code, up_to_minutes, fee_points) VALUES
    ('KHACH', 'XE_MAY', 120, 5000), ('KHACH', 'XE_MAY', 360, 8000), ('KHACH', 'XE_MAY', 1440, 12000),
    ('KHACH', 'XE_MAY_DIEN', 120, 4000), ('KHACH', 'XE_MAY_DIEN', 360, 6000), ('KHACH', 'XE_MAY_DIEN', 1440, 10000),
    ('KHACH', 'XE_DAP', 120, 2000), ('KHACH', 'XE_DAP', 360, 3000), ('KHACH', 'XE_DAP', 1440, 5000);

ALTER TABLE system_config
    ADD COLUMN guest_parking_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN lost_ticket_fee_vnd INT NOT NULL DEFAULT 20000,
    ADD CONSTRAINT ck_system_config_lost_ticket CHECK (lost_ticket_fee_vnd >= 0);

CREATE TABLE guest_ticket_sequences (
    ticket_date DATE PRIMARY KEY,
    next_number INT NOT NULL
);

CREATE TABLE guest_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ticket_code VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ticket_token CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    plate_number VARCHAR(20) NULL,
    vehicle_type_code VARCHAR(20) NOT NULL,
    guest_name VARCHAR(100) NULL,
    guest_phone VARCHAR(15) NULL,
    note VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DANG_GUI',
    lost_ticket BOOLEAN NOT NULL DEFAULT FALSE,
    document_last4 CHAR(4) CHARACTER SET ascii NULL,
    lost_ticket_approved_by BIGINT NULL,
    entered_at DATETIME(6) NOT NULL,
    entry_guard_id BIGINT NOT NULL,
    exited_at DATETIME(6) NULL,
    exit_guard_id BIGINT NULL,
    open_plate VARCHAR(20) GENERATED ALWAYS AS (CASE WHEN status = 'DANG_GUI' THEN plate_number END) STORED,
    CONSTRAINT uq_guest_ticket_code UNIQUE (ticket_code),
    CONSTRAINT uq_guest_ticket_token UNIQUE (ticket_token),
    CONSTRAINT uq_guest_open_plate UNIQUE (open_plate),
    CONSTRAINT fk_guest_vehicle_type FOREIGN KEY (vehicle_type_code) REFERENCES vehicle_types (code),
    CONSTRAINT fk_guest_entry_guard FOREIGN KEY (entry_guard_id) REFERENCES guards (user_id),
    CONSTRAINT fk_guest_exit_guard FOREIGN KEY (exit_guard_id) REFERENCES guards (user_id),
    CONSTRAINT fk_guest_lost_approver FOREIGN KEY (lost_ticket_approved_by) REFERENCES users (id),
    CONSTRAINT ck_guest_plate CHECK (plate_number IS NOT NULL OR vehicle_type_code = 'XE_DAP'),
    CONSTRAINT ck_guest_state CHECK (
        (status = 'DANG_GUI' AND exited_at IS NULL AND exit_guard_id IS NULL AND lost_ticket = FALSE) OR
        (status = 'DA_RA' AND exited_at IS NOT NULL AND exit_guard_id IS NOT NULL)),
    CONSTRAINT ck_guest_time CHECK (exited_at IS NULL OR exited_at >= entered_at)
);
CREATE INDEX ix_guest_sessions_status_time ON guest_sessions (status, entered_at DESC);
CREATE INDEX ix_guest_sessions_entered_at ON guest_sessions (entered_at DESC);
CREATE INDEX ix_guest_sessions_plate ON guest_sessions (plate_number);

CREATE TABLE guest_vietqr_quotes (
    guest_session_id BIGINT PRIMARY KEY,
    quote_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    fee_points BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    overnight BOOLEAN NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_guest_quote_session FOREIGN KEY (guest_session_id) REFERENCES guest_sessions (id),
    CONSTRAINT ck_guest_quote_amount CHECK (fee_points > 0 AND amount > 0)
);

CREATE TABLE guest_payments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    guest_session_id BIGINT NOT NULL,
    guard_id BIGINT NOT NULL,
    shift_id BIGINT NULL,
    method VARCHAR(20) NOT NULL,
    fee_points BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    overnight BOOLEAN NOT NULL DEFAULT FALSE,
    lost_ticket_fee BIGINT NOT NULL DEFAULT 0,
    idempotency_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    paid_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_guest_payment_session UNIQUE (guest_session_id),
    CONSTRAINT uq_guest_payment_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_guest_payment_session FOREIGN KEY (guest_session_id) REFERENCES guest_sessions (id),
    CONSTRAINT fk_guest_payment_guard FOREIGN KEY (guard_id) REFERENCES guards (user_id),
    CONSTRAINT fk_guest_payment_shift FOREIGN KEY (shift_id) REFERENCES guard_shifts (id),
    CONSTRAINT ck_guest_payment_method CHECK (method IN ('TIEN_MAT', 'VIETQR')),
    CONSTRAINT ck_guest_payment_amount CHECK (fee_points > 0 AND amount > lost_ticket_fee AND lost_ticket_fee >= 0)
);
CREATE INDEX ix_guest_payment_paid ON guest_payments (paid_at);
CREATE INDEX ix_guest_payment_guard_paid ON guest_payments (guard_id, paid_at);

CREATE TRIGGER trg_guest_payment_no_update BEFORE UPDATE ON guest_payments FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'guest_payments chi cho phep them moi';
CREATE TRIGGER trg_guest_payment_no_delete BEFORE DELETE ON guest_payments FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'guest_payments chi cho phep them moi';

ALTER TABLE incidents DROP CHECK ck_incidents_type,
    ADD CONSTRAINT ck_incidents_type CHECK (type IN ('XE_GUI_QUA_LAU', 'XE_CHUA_DANG_KY', 'XE_HU_HONG', 'MAT_VE', 'KHAC'));
