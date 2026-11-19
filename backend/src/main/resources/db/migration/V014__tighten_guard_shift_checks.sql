-- =====================================================================
-- V014: vá lỗ hổng ràng buộc CHECK của guard_shifts (V012, V013).
-- Trong SQL, so sánh với NULL (ví dụ NULL BETWEEN 0 AND 50000000) cho kết quả UNKNOWN, và CHECK coi UNKNOWN là ĐẠT.
-- Vì vậy ca "đã kết thúc" nhưng thiếu tiền bàn giao / thiếu số liệu vẫn lọt qua -> phải ghi rõ IS NOT NULL.
-- (Phát hiện khi một backend bản cũ kết thúc ca sau lúc V013 đã chạy: closing_cash bị để NULL.)
-- =====================================================================

-- Sửa dữ liệu đã lỡ ghi thiếu trước khi siết ràng buộc
UPDATE guard_shifts SET closing_cash = 0 WHERE ended_at IS NOT NULL AND closing_cash IS NULL;
UPDATE guard_shifts
SET check_ins = COALESCE(check_ins, 0), check_outs = COALESCE(check_outs, 0),
    vehicles_in_lot = COALESCE(vehicles_in_lot, 0), open_incidents = COALESCE(open_incidents, 0)
WHERE ended_at IS NOT NULL;

ALTER TABLE guard_shifts DROP CHECK ck_guard_shifts_summary;
ALTER TABLE guard_shifts DROP CHECK ck_guard_shifts_cash;

ALTER TABLE guard_shifts
    ADD CONSTRAINT ck_guard_shifts_summary CHECK (
        (ended_at IS NULL AND check_ins IS NULL AND check_outs IS NULL
            AND vehicles_in_lot IS NULL AND open_incidents IS NULL)
     OR (ended_at IS NOT NULL
            AND check_ins IS NOT NULL AND check_ins >= 0
            AND check_outs IS NOT NULL AND check_outs >= 0
            AND vehicles_in_lot IS NOT NULL AND vehicles_in_lot >= 0
            AND open_incidents IS NOT NULL AND open_incidents >= 0)
    ),
    ADD CONSTRAINT ck_guard_shifts_cash CHECK (
        opening_cash BETWEEN 0 AND 50000000
        AND (expected_opening_cash IS NULL OR expected_opening_cash >= 0)
        AND ((ended_at IS NULL AND closing_cash IS NULL)
          OR (ended_at IS NOT NULL AND closing_cash IS NOT NULL AND closing_cash BETWEEN 0 AND 50000000))
    );
