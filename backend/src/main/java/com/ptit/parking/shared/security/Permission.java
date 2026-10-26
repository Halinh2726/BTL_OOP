package com.ptit.parking.shared.security;

/**
 * Mã quyền (bảng permissions, Đặc tả API mục 4). Tên hằng trùng đúng mã trong CSDL.
 * <p>Vai trò nào có quyền nào là DỮ LIỆU (bảng role_permissions), không hóa cứng ở đây:
 * enum này chỉ giúp code tham chiếu mã quyền an toàn kiểu, gõ sai là lỗi biên dịch.
 */
public enum Permission {
    VEHICLE_REGISTER,
    WALLET_VIEW_OWN,
    PARKING_CHECK_IN,
    PARKING_CHECK_OUT,
    CASH_DEPOSIT,
    CASH_WITHDRAWAL,
    GUARD_CREATE,
    GUARD_LOCK
}
