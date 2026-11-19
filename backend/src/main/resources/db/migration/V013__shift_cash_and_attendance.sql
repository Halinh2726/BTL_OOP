-- =====================================================================
-- V013 (Đợt 2 bổ sung — chấm công & tiền mặt khi nhận / bàn giao ca):
--   opening_cash          : số tiền mặt bảo vệ KHAI đang cầm lúc nhận ca (tiền lẻ, tiền thu hộ)
--   expected_opening_cash : tổng số tiền các ca trước đã bàn giao lại (NULL nếu không nhận biên bản nào)
--                           -> chênh lệch nhận ca = opening_cash - expected_opening_cash
--   closing_cash          : số tiền mặt bảo vệ KHAI bàn giao lúc kết thúc ca
-- THAY ĐỔI ĐẶC TẢ so với R06: bảo vệ CHỈ khai báo số tiền đang cầm; vẫn không xem doanh thu, không xem điểm của ai.
-- Mỗi ca tối đa 50.000.000 đ để chặn gõ nhầm số.
-- =====================================================================

ALTER TABLE guard_shifts
    ADD COLUMN opening_cash          BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN expected_opening_cash BIGINT NULL,
    ADD COLUMN closing_cash          BIGINT NULL;

-- Ca đã kết thúc từ trước khi có tính năng này: coi như bàn giao 0 đ
UPDATE guard_shifts SET closing_cash = 0 WHERE ended_at IS NOT NULL;

ALTER TABLE guard_shifts
    ADD CONSTRAINT ck_guard_shifts_cash CHECK (
        opening_cash BETWEEN 0 AND 50000000
        AND (expected_opening_cash IS NULL OR expected_opening_cash >= 0)
        AND ((ended_at IS NULL AND closing_cash IS NULL)
          OR (ended_at IS NOT NULL AND closing_cash BETWEEN 0 AND 50000000))
    );

-- Chấm công: quản lý lọc các ca theo khoảng ngày, theo bảo vệ
CREATE INDEX ix_guard_shifts_started ON guard_shifts (started_at, guard_id);
