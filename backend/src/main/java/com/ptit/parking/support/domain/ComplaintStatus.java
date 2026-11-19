package com.ptit.parking.support.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Máy trạng thái của khiếu nại — mỗi trạng thái tự biết được chuyển sang đâu ({@link #canMoveTo}):
 * <pre>
 *   MOI ──tiếp nhận──▶ DANG_XU_LY ──kết luận──▶ DA_XU_LY
 *    │                    │  └──đề xuất bồi thường──▶ CHO_DUYET_BOI_THUONG ──duyệt──▶ DA_BOI_THUONG
 *    │                    │                                              └──từ chối──▶ KHONG_BOI_THUONG
 *    ├──hủy (sinh viên)──▶ DA_HUY
 *    └────────────────────┴──từ chối──▶ TU_CHOI
 * </pre>
 */
public enum ComplaintStatus {
    MOI,
    DANG_XU_LY,
    CHO_DUYET_BOI_THUONG,
    DA_XU_LY,
    TU_CHOI,
    DA_HUY,
    DA_BOI_THUONG,
    KHONG_BOI_THUONG;

    public boolean canMoveTo(ComplaintStatus next) {
        return targets().contains(next);
    }

    /** Đã kết thúc: không còn chuyển đi đâu. */
    public boolean isClosed() {
        return targets().isEmpty();
    }

    private Set<ComplaintStatus> targets() {
        return switch (this) {
            case MOI -> EnumSet.of(DANG_XU_LY, TU_CHOI, DA_HUY);
            case DANG_XU_LY -> EnumSet.of(DA_XU_LY, TU_CHOI, CHO_DUYET_BOI_THUONG);
            case CHO_DUYET_BOI_THUONG -> EnumSet.of(DA_BOI_THUONG, KHONG_BOI_THUONG);
            case DA_XU_LY, TU_CHOI, DA_HUY, DA_BOI_THUONG, KHONG_BOI_THUONG -> EnumSet.noneOf(ComplaintStatus.class);
        };
    }
}
