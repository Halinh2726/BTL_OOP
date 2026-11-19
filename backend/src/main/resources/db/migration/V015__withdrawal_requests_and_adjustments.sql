-- =====================================================================
-- V015 (Đợt 3 — rút điểm theo yêu cầu, giao dịch điều chỉnh):
--   1. Loại giao dịch thứ 4 DIEU_CHINH: chuyển điểm giữa hai ví để sửa sai sót (hoàn điểm trừ nhầm, thu hồi điểm
--      cộng nhầm, bồi thường). KHÔNG sửa giao dịch cũ (R10) — ghi một giao dịch mới, bắt buộc có lý do.
--      Là phép CHUYỂN nên tổng điểm không đổi: R12 (bảo toàn điểm) vẫn đúng, view v_reconciliation không phải sửa.
--   2. withdrawal_requests: chủ bãi xe TẠO yêu cầu rút, quản lý TRẢ TIỀN / TỪ CHỐI, chủ bãi XÁC NHẬN / KHIẾU NẠI.
--      R15: tổng yêu cầu đang chờ không vượt số điểm hiện có (use case kiểm tra, có khóa ví)
--      R16: rút tiền mặt không vượt quỹ tiền mặt quản lý đang giữ (use case kiểm tra)
--      R17: mỗi yêu cầu chỉ trả MỘT lần — UNIQUE transaction_id
--      R18: người tạo yêu cầu không được là người xử lý — CHECK processed_by <> owner_id
--      Bản này chỉ rút TIỀN MẶT; chuyển khoản cần quỹ điện tử (nạp điểm online) nên chưa mở.
--   3. Quyền: OWNER_FINANCE (chủ bãi xe), POINT_ADJUST (quản lý). CASH_WITHDRAWAL nay nghĩa là "xử lý yêu cầu rút".
-- =====================================================================

-- ---------- 1. Giao dịch điều chỉnh ----------
ALTER TABLE point_transactions DROP CHECK ck_point_transactions_type;
ALTER TABLE point_transactions DROP CHECK ck_point_transactions_shape;

ALTER TABLE point_transactions
    ADD CONSTRAINT ck_point_transactions_type CHECK (type IN ('DOI_DIEM', 'THANH_TOAN', 'RUT_DIEM', 'DIEU_CHINH')),
    ADD CONSTRAINT ck_point_transactions_shape CHECK (
        (type = 'DOI_DIEM'
            AND source_wallet_id IS NULL     AND target_wallet_id IS NOT NULL
            AND source_balance_after IS NULL AND target_balance_after IS NOT NULL)
     OR (type = 'RUT_DIEM'
            AND source_wallet_id IS NOT NULL AND target_wallet_id IS NULL
            AND source_balance_after IS NOT NULL AND target_balance_after IS NULL)
     OR (type IN ('THANH_TOAN', 'DIEU_CHINH')
            AND source_wallet_id IS NOT NULL AND target_wallet_id IS NOT NULL
            AND source_wallet_id <> target_wallet_id
            AND source_balance_after IS NOT NULL AND target_balance_after IS NOT NULL)
    );

CREATE TABLE adjustment_details (
    transaction_id          BIGINT       NOT NULL PRIMARY KEY,
    original_transaction_id BIGINT       NULL,          -- giao dịch bị sai mà điều chỉnh này sửa (nếu có)
    reason                  VARCHAR(500) NOT NULL,

    CONSTRAINT fk_adjustment_details_transaction FOREIGN KEY (transaction_id)          REFERENCES point_transactions (id),
    CONSTRAINT fk_adjustment_details_original    FOREIGN KEY (original_transaction_id) REFERENCES point_transactions (id),
    CONSTRAINT ck_adjustment_details_reason      CHECK (CHAR_LENGTH(TRIM(reason)) BETWEEN 1 AND 500)
);

CREATE TRIGGER trg_adjustment_details_no_update BEFORE UPDATE ON adjustment_details FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'R10: adjustment_details chi cho phep them moi';

CREATE TRIGGER trg_adjustment_details_no_delete BEFORE DELETE ON adjustment_details FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'R10: adjustment_details chi cho phep them moi';

-- ---------- 2. Yêu cầu rút điểm ----------
CREATE TABLE withdrawal_requests (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id         BIGINT       NOT NULL,
    points           BIGINT       NOT NULL,
    method           VARCHAR(20)  NOT NULL DEFAULT 'TIEN_MAT',
    cash_per_point   INT          NOT NULL,               -- tỷ lệ lúc tạo (R11), số tiền = points × cash_per_point
    status           VARCHAR(20)  NOT NULL DEFAULT 'CHO_XU_LY',
    created_at       DATETIME(6)  NOT NULL,
    cancelled_at     DATETIME(6)  NULL,
    processed_by     BIGINT       NULL,                   -- quản lý trả tiền / từ chối
    processed_at     DATETIME(6)  NULL,
    reject_reason    VARCHAR(500) NULL,
    transaction_id   BIGINT       NULL,                   -- giao dịch RUT_DIEM khi đã trả
    confirmed_at     DATETIME(6)  NULL,
    complaint_note   VARCHAR(500) NULL,
    complained_at    DATETIME(6)  NULL,
    resolution_note  VARCHAR(500) NULL,
    resolved_by      BIGINT       NULL,
    resolved_at      DATETIME(6)  NULL,

    CONSTRAINT uq_withdrawal_requests_transaction UNIQUE (transaction_id),                         -- R17
    CONSTRAINT fk_withdrawal_requests_owner       FOREIGN KEY (owner_id)       REFERENCES users (id),
    CONSTRAINT fk_withdrawal_requests_processor   FOREIGN KEY (processed_by)   REFERENCES users (id),
    CONSTRAINT fk_withdrawal_requests_resolver    FOREIGN KEY (resolved_by)    REFERENCES users (id),
    CONSTRAINT fk_withdrawal_requests_transaction FOREIGN KEY (transaction_id) REFERENCES point_transactions (id),
    CONSTRAINT ck_withdrawal_requests_points      CHECK (points > 0),
    CONSTRAINT ck_withdrawal_requests_rate        CHECK (cash_per_point > 0),
    CONSTRAINT ck_withdrawal_requests_method      CHECK (method IN ('TIEN_MAT')),
    CONSTRAINT ck_withdrawal_requests_status      CHECK (status IN
        ('CHO_XU_LY', 'DA_TRA', 'DA_XAC_NHAN', 'TU_CHOI', 'DA_HUY', 'KHIEU_NAI')),
    -- R18: không ai tự xử lý / tự giải quyết yêu cầu của chính mình
    CONSTRAINT ck_withdrawal_requests_separation  CHECK (
        (processed_by IS NULL OR processed_by <> owner_id) AND (resolved_by IS NULL OR resolved_by <> owner_id)
    ),
    -- Đã trả (và các bước sau) thì PHẢI có giao dịch; chưa trả thì KHÔNG có
    CONSTRAINT ck_withdrawal_requests_paid        CHECK (
        (status IN ('DA_TRA', 'DA_XAC_NHAN', 'KHIEU_NAI') AND transaction_id IS NOT NULL AND processed_by IS NOT NULL)
     OR (status IN ('CHO_XU_LY', 'TU_CHOI', 'DA_HUY') AND transaction_id IS NULL)
    ),
    CONSTRAINT ck_withdrawal_requests_rejected    CHECK (status <> 'TU_CHOI' OR (reject_reason IS NOT NULL AND processed_by IS NOT NULL))
);

CREATE INDEX ix_withdrawal_requests_status ON withdrawal_requests (status, id DESC);
CREATE INDEX ix_withdrawal_requests_owner  ON withdrawal_requests (owner_id, id DESC);

-- ---------- 3. Quyền ----------
INSERT INTO permissions (code, description) VALUES
    ('OWNER_FINANCE', 'Chủ bãi xe: xem tổng quan, doanh thu; tạo / hủy / xác nhận yêu cầu rút điểm'),
    ('POINT_ADJUST',  'Ghi giao dịch điều chỉnh (hoàn điểm / thu hồi điểm) kèm lý do');

INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('CHU_TAI_KHOAN', 'OWNER_FINANCE'),
    ('QUAN_LY',       'POINT_ADJUST');

UPDATE permissions SET description = 'Xử lý yêu cầu rút điểm của chủ bãi xe (trả tiền mặt / từ chối / giải quyết khiếu nại)'
WHERE code = 'CASH_WITHDRAWAL';
