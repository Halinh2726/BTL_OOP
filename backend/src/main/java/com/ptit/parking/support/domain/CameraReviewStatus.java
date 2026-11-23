package com.ptit.parking.support.domain;

/**
 * Trạng thái trích xuất dữ liệu camera cho khiếu nại / yêu cầu của sinh viên (khớp ck_complaints_camera_status).
 */
public enum CameraReviewStatus {
    KHONG_YEU_CAU,
    CHO_TRICH_XUAT,
    DANG_TRICH_XUAT,
    DA_TRICH_XUAT,
    KHONG_CO_DU_LIEU;

    public boolean isRequested() {
        return this != KHONG_YEU_CAU;
    }
}
