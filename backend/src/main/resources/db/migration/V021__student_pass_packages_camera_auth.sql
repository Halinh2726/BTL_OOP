-- =====================================================================
-- V021: Tối ưu Sinh viên & Authentication
--   1. pass_packages: gói vé tháng ưu đãi (1 tháng, Quý 3 tháng -10%, Học kỳ 5 tháng -15%),
--      gắn với monthly_passes. Cho phép nhiều tháng của một gói trỏ về cùng một transaction MUA_VE.
--   2. complaints + complaint_camera_photos: bổ sung loại TRICH_XUAT_CAMERA, DICH_VU_KHAC
--      và thông tin/ảnh trích xuất camera phản hồi từ quản lý.
-- =====================================================================

-- ---------- 1. Gói ưu đãi vé tháng ----------
CREATE TABLE pass_packages (
    code             VARCHAR(30)  NOT NULL PRIMARY KEY,
    name             VARCHAR(100) NOT NULL,
    duration_months  INT          NOT NULL,
    discount_percent INT          NOT NULL DEFAULT 0,
    description      VARCHAR(255) NULL,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order       INT          NOT NULL DEFAULT 0,

    CONSTRAINT ck_pass_packages_duration CHECK (duration_months BETWEEN 1 AND 12),
    CONSTRAINT ck_pass_packages_discount CHECK (discount_percent BETWEEN 0 AND 50)
);

INSERT INTO pass_packages (code, name, duration_months, discount_percent, description, active, sort_order) VALUES
    ('THANG_1',  'Vé tháng lẻ (1 tháng)',         1,  0, 'Gửi xe miễn phí trọn 1 tháng dương lịch',                   TRUE, 1),
    ('QUY_3',    'Gói Quý ưu đãi (3 tháng)',       3, 10, 'Tiết kiệm 10% — gửi miễn phí 3 tháng liên tiếp',          TRUE, 2),
    ('HOC_KY_5', 'Gói Học kỳ tiết kiệm (5 tháng)', 5, 15, 'Tiết kiệm 15% — gửi miễn phí trọn học kỳ 5 tháng liên tiếp', TRUE, 3);

-- Tạo index thường trước khi xóa UNIQUE index để giữ nguyên khóa ngoại fk_monthly_passes_transaction trong InnoDB
CREATE INDEX ix_monthly_passes_transaction ON monthly_passes (transaction_id);
ALTER TABLE monthly_passes DROP INDEX uq_monthly_passes_transaction;

ALTER TABLE monthly_passes
    ADD COLUMN package_code VARCHAR(30) NOT NULL DEFAULT 'THANG_1' AFTER price_points,
    ADD CONSTRAINT fk_monthly_passes_package FOREIGN KEY (package_code) REFERENCES pass_packages (code);

-- ---------- 2. Trích xuất camera & dịch vụ khác cho khiếu nại ----------
ALTER TABLE complaints DROP CHECK ck_complaints_type;

ALTER TABLE complaints
    ADD COLUMN camera_requested    BOOLEAN       NOT NULL DEFAULT FALSE,
    ADD COLUMN camera_status       VARCHAR(30)   NOT NULL DEFAULT 'KHONG_YEU_CAU',
    ADD COLUMN incident_area       VARCHAR(150)  NULL,
    ADD COLUMN incident_time_range VARCHAR(100)  NULL,
    ADD COLUMN camera_note         VARCHAR(1000) NULL,
    ADD COLUMN camera_reviewed_by  BIGINT        NULL,
    ADD COLUMN camera_reviewed_at  DATETIME(6)   NULL,
    ADD CONSTRAINT ck_complaints_type CHECK (type IN (
        'THU_PHI_SAI', 'XE_HU_HONG', 'MAT_DO', 'TRICH_XUAT_CAMERA', 'DICH_VU_KHAC', 'KHAC'
    )),
    ADD CONSTRAINT fk_complaints_camera_reviewer FOREIGN KEY (camera_reviewed_by) REFERENCES users (id),
    ADD CONSTRAINT ck_complaints_camera_status CHECK (camera_status IN (
        'KHONG_YEU_CAU', 'CHO_TRICH_XUAT', 'DANG_TRICH_XUAT', 'DA_TRICH_XUAT', 'KHONG_CO_DU_LIEU'
    ));

CREATE TABLE complaint_camera_photos (
    complaint_id BIGINT      NOT NULL PRIMARY KEY,
    content_type VARCHAR(30) NOT NULL,
    data         MEDIUMBLOB  NOT NULL,
    uploaded_at  DATETIME(6) NOT NULL,

    CONSTRAINT fk_complaint_camera_photos_complaint FOREIGN KEY (complaint_id) REFERENCES complaints (id),
    CONSTRAINT ck_complaint_camera_photos_type      CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp'))
);
