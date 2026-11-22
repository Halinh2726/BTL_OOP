-- =====================================================================
-- V011: Cấu hình tài khoản ngân hàng nhận tiền VietQR (tùy chỉnh bởi Quản lý).
-- Bảng có tối đa 1 dòng (id = 1). Nếu bảng trống, ứng dụng tự dùng giá trị
-- mặc định từ biến môi trường (.env / application.yml).
-- =====================================================================

CREATE TABLE bank_config (
    id           SMALLINT     NOT NULL DEFAULT 1 PRIMARY KEY,
    bank_id      VARCHAR(20)  NOT NULL,
    account_no   VARCHAR(30)  NOT NULL,
    account_name VARCHAR(100) NOT NULL,
    template     VARCHAR(30)  NOT NULL DEFAULT 'compact2',
    updated_by   BIGINT       NULL,
    updated_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT ck_bank_config_one_row      CHECK (id = 1),
    CONSTRAINT ck_bank_config_bank_id      CHECK (REGEXP_LIKE(bank_id, '^[A-Z0-9]{2,20}$', 'c')),
    CONSTRAINT ck_bank_config_account_no   CHECK (REGEXP_LIKE(account_no, '^[A-Z0-9]{4,30}$', 'c')),
    CONSTRAINT ck_bank_config_account_name CHECK (CHAR_LENGTH(TRIM(account_name)) BETWEEN 1 AND 100),
    CONSTRAINT ck_bank_config_template     CHECK (template IN ('compact2', 'compact', 'qr_only', 'print')),
    CONSTRAINT fk_bank_config_updated_by   FOREIGN KEY (updated_by) REFERENCES users (id)
);
