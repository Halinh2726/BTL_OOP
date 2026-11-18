package com.ptit.parking.shared.application.port.out;

import java.time.Instant;
import java.util.Objects;

/**
 * Port: nhật ký thao tác trên tài khoản (bảng audit_logs) — "ai, làm gì, với tài khoản nào, lúc nào".
 * Chỉ THÊM, không sửa/xóa (cùng tinh thần R10). Đặt ở shared vì nhiều module cùng ghi: account (mật khẩu,
 * thông tin cá nhân), manager (tạo/khóa bảo vệ).
 * <p>{@link #NONE} là mẫu <b>Null Object</b>: nơi không cần ghi nhật ký (ví dụ unit test) dùng đối tượng
 * "không làm gì" thay vì kiểm tra {@code null} ở khắp nơi.
 */
public interface AuditTrail {

    /** Loại thao tác — tên hằng là mã lưu ở cột audit_logs.action. */
    enum AuditAction {
        CAP_NHAT_THONG_TIN,
        DOI_MAT_KHAU,
        YEU_CAU_KHOI_PHUC_MAT_KHAU,
        KHOI_PHUC_MAT_KHAU,
        CAP_MAT_KHAU_TAM,
        TAO_BAO_VE,
        CAP_NHAT_BAO_VE,
        KHOA_TAI_KHOAN,
        MO_KHOA_TAI_KHOAN
    }

    /** Một dòng nhật ký. {@code targetUserId} null khi thao tác không nhắm vào tài khoản cụ thể. */
    record AuditEntry(long actorId, AuditAction action, Long targetUserId, String detail, Instant at) {

        public static final int MAX_DETAIL = 500;

        public AuditEntry {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(at, "at");
            detail = detail == null ? "" : detail.strip();
            if (detail.length() > MAX_DETAIL) {
                detail = detail.substring(0, MAX_DETAIL);
            }
        }
    }

    void record(AuditEntry entry);

    /** Null Object: bỏ qua mọi dòng nhật ký. */
    AuditTrail NONE = entry -> {
    };
}
