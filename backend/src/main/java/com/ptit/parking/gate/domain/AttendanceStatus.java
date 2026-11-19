package com.ptit.parking.gate.domain;

/** Kết quả đối chiếu một ca được phân với giờ bảo vệ thực sự bấm "Nhận ca". */
public enum AttendanceStatus {
    /** Ca chưa tới giờ. */
    SAP_TOI,
    /** Đã tới giờ, ca chưa kết thúc, bảo vệ chưa nhận ca. */
    CHUA_NHAN_CA,
    /** Nhận ca trước giờ hoặc muộn không quá {@link ShiftAssignment#GRACE}. */
    DUNG_GIO,
    /** Nhận ca muộn hơn {@link ShiftAssignment#GRACE}. */
    DI_MUON,
    /** Hết ca mà không nhận ca. */
    VANG
}
