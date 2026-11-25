-- =====================================================================
-- V022: Bỏ gói ưu đãi / giảm giá vé tháng.
-- Sinh viên chọn mua 1 tháng mặc định hoặc mua gộp nhiều tháng (1..12 tháng)
-- theo nguyên giá monthly_points * months_count, không giảm giá.
-- =====================================================================

ALTER TABLE monthly_passes DROP FOREIGN KEY fk_monthly_passes_package;
ALTER TABLE monthly_passes DROP COLUMN package_code;
DROP TABLE pass_packages;
