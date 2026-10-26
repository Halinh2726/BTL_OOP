-- =====================================================================
-- V007: Cấu hình cố định (không có giao diện cấu hình — đặc tả mục 3)
--   fee_rules     : bảng giá theo khung giờ ra (R03), dùng bởi TimeSlotFeePolicy
--   system_config : tỷ lệ quy đổi + ví nhận phí của chủ tài khoản (R06)
-- =====================================================================

CREATE TABLE fee_rules (
    id         BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    start_time TIME   NOT NULL,                      -- giờ bắt đầu khung, giờ Việt Nam
    fee_points INT    NOT NULL,

    CONSTRAINT uq_fee_rules_start  UNIQUE (start_time),
    CONSTRAINT ck_fee_rules_points CHECK (fee_points > 0)
);

-- Bảng chỉ có đúng 1 dòng (id = 1)
CREATE TABLE system_config (
    id                  SMALLINT NOT NULL DEFAULT 1 PRIMARY KEY,
    cash_per_point      INT      NOT NULL,           -- số đồng cho 1 điểm
    receiving_wallet_id BIGINT   NOT NULL,           -- ví chủ tài khoản: nơi DUY NHẤT nhận phí gửi xe

    CONSTRAINT fk_system_config_wallet  FOREIGN KEY (receiving_wallet_id) REFERENCES wallets (id),
    CONSTRAINT ck_system_config_one_row CHECK (id = 1),
    CONSTRAINT ck_system_config_rate    CHECK (cash_per_point > 0)
);
