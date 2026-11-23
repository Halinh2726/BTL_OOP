package com.ptit.parking.support.domain;

/**
 * Loại khiếu nại (khớp ck_complaints_type). Mỗi loại tự biết được giải quyết bằng tiền theo cách nào — nơi dùng không
 * cần if/else theo từng loại:
 * <ul>
 *   <li>Thu phí sai: quản lý tự HOÀN PHÍ (giao dịch điều chỉnh) khi kết luận.</li>
 *   <li>Xe hư hỏng, mất đồ: quản lý ĐỀ XUẤT bồi thường, chủ bãi xe DUYỆT.</li>
 * </ul>
 */
public enum ComplaintType {
    THU_PHI_SAI(true, false),
    XE_HU_HONG(false, true),
    MAT_DO(false, true),
    TRICH_XUAT_CAMERA(false, false),
    DICH_VU_KHAC(false, false),
    KHAC(false, false);

    private final boolean refundable;
    private final boolean compensable;

    ComplaintType(boolean refundable, boolean compensable) {
        this.refundable = refundable;
        this.compensable = compensable;
    }

    /** Quản lý được hoàn phí ngay khi kết luận. */
    public boolean allowsRefund() {
        return refundable;
    }

    /** Được đề xuất bồi thường (cần chủ bãi xe duyệt). */
    public boolean allowsCompensation() {
        return compensable;
    }

    /** Loại khiếu nại mặc định kèm yêu cầu trích xuất camera. */
    public boolean defaultCameraRequest() {
        return this == TRICH_XUAT_CAMERA;
    }
}
