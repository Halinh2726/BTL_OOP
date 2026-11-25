-- V029: Cấu hình gửi email SMTP động (Quản lý cấu hình trực tiếp hoặc dùng mặc định từ .env).
CREATE TABLE smtp_config (
    id           SMALLINT     NOT NULL DEFAULT 1 PRIMARY KEY,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    host         VARCHAR(150) NOT NULL,
    port         INT          NOT NULL DEFAULT 587,
    username     VARCHAR(150) NOT NULL,
    password     VARCHAR(255) NOT NULL,
    from_address VARCHAR(150) NOT NULL,
    starttls     BOOLEAN      NOT NULL DEFAULT TRUE,
    ssl_enabled  BOOLEAN      NOT NULL DEFAULT FALSE,
    alert_email  VARCHAR(150) NULL,
    updated_by   BIGINT       NULL,
    updated_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT ck_smtp_config_one_row CHECK (id = 1),
    CONSTRAINT ck_smtp_config_port    CHECK (port BETWEEN 1 AND 65535),
    CONSTRAINT fk_smtp_config_user    FOREIGN KEY (updated_by) REFERENCES users (id)
);
