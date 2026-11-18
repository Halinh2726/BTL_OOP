package com.ptit.parking.student.domain;

/** Trạng thái xe — khớp ck_vehicles_status. "Xóa xe" chỉ là chuyển sang NGUNG_SU_DUNG: lịch sử lượt gửi vẫn còn. */
public enum VehicleStatus {
    DANG_DUNG,
    NGUNG_SU_DUNG
}
