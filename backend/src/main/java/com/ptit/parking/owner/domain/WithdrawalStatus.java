package com.ptit.parking.owner.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Trạng thái yêu cầu rút điểm (khớp ck_withdrawal_requests_status). Mỗi trạng thái TỰ BIẾT được chuyển sang đâu —
 * máy trạng thái nằm trong enum, không rải if/else ở use case:
 * <pre>
 *   CHO_XU_LY ──trả tiền──▶ DA_TRA ──chủ bãi xác nhận──▶ DA_XAC_NHAN
 *       │                     └──báo chưa nhận──▶ KHIEU_NAI ──quản lý kiểm tra xong──▶ DA_XAC_NHAN
 *       ├──quản lý từ chối──▶ TU_CHOI
 *       └──chủ bãi hủy─────▶ DA_HUY
 * </pre>
 */
public enum WithdrawalStatus {
    CHO_XU_LY,
    DA_TRA,
    DA_XAC_NHAN,
    TU_CHOI,
    DA_HUY,
    KHIEU_NAI;

    /** Được chuyển từ trạng thái này sang {@code next} không. */
    public boolean canMoveTo(WithdrawalStatus next) {
        return nextStates().contains(next);
    }

    /** Đang chờ quản lý xử lý — điểm của yêu cầu này bị "giữ chỗ" (R15). */
    public boolean isPending() {
        return this == CHO_XU_LY;
    }

    /** Đã trả tiền (điểm đã bị trừ, đã có giao dịch RUT_DIEM). */
    public boolean isPaid() {
        return this == DA_TRA || this == DA_XAC_NHAN || this == KHIEU_NAI;
    }

    private Set<WithdrawalStatus> nextStates() {
        return switch (this) {
            case CHO_XU_LY -> EnumSet.of(DA_TRA, TU_CHOI, DA_HUY);
            case DA_TRA -> EnumSet.of(DA_XAC_NHAN, KHIEU_NAI);
            case KHIEU_NAI -> EnumSet.of(DA_XAC_NHAN);
            case DA_XAC_NHAN, TU_CHOI, DA_HUY -> EnumSet.noneOf(WithdrawalStatus.class);
        };
    }
}
