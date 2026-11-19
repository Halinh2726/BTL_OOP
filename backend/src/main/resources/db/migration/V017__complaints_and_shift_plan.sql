-- =====================================================================
-- V017 (Đợt 4b): sự cố & nhân sự
--   1. complaints, complaint_photos : sinh viên khiếu nại thu phí sai / báo xe hư hỏng / mất đồ; quản lý xử lý,
--      hoàn phí (giao dịch điều chỉnh); bồi thường cần CHỦ BÃI XE duyệt (người đề xuất không tự duyệt).
--   2. shift_assignments           : quản lý phân ca bảo vệ theo ngày (ca sáng / chiều / đêm).
--   3. Quyền mới.
-- =====================================================================

INSERT INTO permissions (code, description) VALUES
    ('COMPLAINT_SUBMIT',     'Sinh viên gửi khiếu nại / báo hư hỏng và theo dõi kết quả'),
    ('CASE_HANDLE',          'Xử lý sự cố bảo vệ báo và khiếu nại của sinh viên; đề xuất bồi thường'),
    ('COMPENSATION_APPROVE', 'Duyệt / từ chối đề xuất bồi thường (trả bằng điểm từ ví chủ bãi xe)'),
    ('SHIFT_PLAN',           'Phân ca trực cho bảo vệ');

INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('SINH_VIEN',     'COMPLAINT_SUBMIT'),
    ('QUAN_LY',       'CASE_HANDLE'),
    ('CHU_TAI_KHOAN', 'COMPENSATION_APPROVE'),
    ('QUAN_LY',       'SHIFT_PLAN');

-- ---------- 1. Khiếu nại ----------
CREATE TABLE complaints (
    id                          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    student_id                  BIGINT        NOT NULL,
    type                        VARCHAR(20)   NOT NULL,
    session_id                  BIGINT        NULL,                -- lượt gửi liên quan (không bắt buộc)
    description                 VARCHAR(1000) NOT NULL,
    status                      VARCHAR(30)   NOT NULL DEFAULT 'MOI',
    created_at                  DATETIME(6)   NOT NULL,
    cancelled_at                DATETIME(6)   NULL,
    handled_by                  BIGINT        NULL,                -- quản lý tiếp nhận / kết luận
    handled_at                  DATETIME(6)   NULL,
    resolution                  VARCHAR(1000) NULL,
    refund_transaction_id       BIGINT        NULL,                -- hoàn phí thu sai (quản lý)
    proposed_points             BIGINT        NULL,                -- đề xuất bồi thường (quản lý)
    proposed_by                 BIGINT        NULL,
    proposed_at                 DATETIME(6)   NULL,
    decided_by                  BIGINT        NULL,                -- chủ bãi xe duyệt / từ chối
    decided_at                  DATETIME(6)   NULL,
    decision_note               VARCHAR(500)  NULL,
    compensation_transaction_id BIGINT        NULL,

    CONSTRAINT uq_complaints_refund       UNIQUE (refund_transaction_id),
    CONSTRAINT uq_complaints_compensation UNIQUE (compensation_transaction_id),
    CONSTRAINT fk_complaints_student      FOREIGN KEY (student_id)  REFERENCES students (user_id),
    CONSTRAINT fk_complaints_session      FOREIGN KEY (session_id)  REFERENCES parking_sessions (id),
    CONSTRAINT fk_complaints_handler      FOREIGN KEY (handled_by)  REFERENCES users (id),
    CONSTRAINT fk_complaints_proposer     FOREIGN KEY (proposed_by) REFERENCES users (id),
    CONSTRAINT fk_complaints_decider      FOREIGN KEY (decided_by)  REFERENCES users (id),
    CONSTRAINT fk_complaints_refund       FOREIGN KEY (refund_transaction_id)       REFERENCES point_transactions (id),
    CONSTRAINT fk_complaints_compensation FOREIGN KEY (compensation_transaction_id) REFERENCES point_transactions (id),
    CONSTRAINT ck_complaints_type   CHECK (type IN ('THU_PHI_SAI', 'XE_HU_HONG', 'MAT_DO', 'KHAC')),
    CONSTRAINT ck_complaints_status CHECK (status IN ('MOI', 'DANG_XU_LY', 'DA_XU_LY', 'TU_CHOI', 'DA_HUY',
                                                      'CHO_DUYET_BOI_THUONG', 'DA_BOI_THUONG', 'KHONG_BOI_THUONG')),
    CONSTRAINT ck_complaints_text   CHECK (CHAR_LENGTH(TRIM(description)) BETWEEN 1 AND 1000),
    CONSTRAINT ck_complaints_points CHECK (proposed_points IS NULL OR proposed_points > 0),
    -- Đã đề xuất bồi thường thì phải biết bao nhiêu điểm và ai đề xuất
    CONSTRAINT ck_complaints_proposal CHECK (
        status NOT IN ('CHO_DUYET_BOI_THUONG', 'DA_BOI_THUONG', 'KHONG_BOI_THUONG')
        OR (proposed_points IS NOT NULL AND proposed_by IS NOT NULL)
    ),
    -- Đã bồi thường thì phải có giao dịch; người duyệt KHÁC người đề xuất (tương tự R18)
    CONSTRAINT ck_complaints_paid CHECK (status <> 'DA_BOI_THUONG' OR compensation_transaction_id IS NOT NULL),
    CONSTRAINT ck_complaints_four_eyes CHECK (decided_by IS NULL OR proposed_by IS NULL OR decided_by <> proposed_by)
);

CREATE INDEX ix_complaints_student ON complaints (student_id, id DESC);
CREATE INDEX ix_complaints_status  ON complaints (status, id DESC);

CREATE TABLE complaint_photos (
    complaint_id BIGINT      NOT NULL PRIMARY KEY,
    content_type VARCHAR(30) NOT NULL,
    data         MEDIUMBLOB  NOT NULL,

    CONSTRAINT fk_complaint_photos_complaint FOREIGN KEY (complaint_id) REFERENCES complaints (id),
    CONSTRAINT ck_complaint_photos_type      CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp'))
);

-- ---------- 2. Phân ca ----------
CREATE TABLE shift_assignments (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    work_date   DATE        NOT NULL,
    slot        VARCHAR(10) NOT NULL,
    guard_id    BIGINT      NOT NULL,
    assigned_by BIGINT      NOT NULL,
    assigned_at DATETIME(6) NOT NULL,

    -- Một bảo vệ không bị xếp hai lần vào cùng một ca
    CONSTRAINT uq_shift_assignments UNIQUE (work_date, slot, guard_id),
    CONSTRAINT fk_shift_assignments_guard    FOREIGN KEY (guard_id)    REFERENCES guards (user_id),
    CONSTRAINT fk_shift_assignments_assigner FOREIGN KEY (assigned_by) REFERENCES users (id),
    CONSTRAINT ck_shift_assignments_slot     CHECK (slot IN ('SANG', 'CHIEU', 'DEM'))
);

CREATE INDEX ix_shift_assignments_guard ON shift_assignments (guard_id, work_date);
