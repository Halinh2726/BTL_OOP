package com.ptit.parking.payment.domain;

/** Loại giao dịch (point_transactions.type). Tên hằng trùng đúng mã trong CSDL. */
public enum TransactionType {
    DOI_DIEM,
    THANH_TOAN,
    RUT_DIEM,
    /** Đợt 3: chuyển điểm sửa sai sót, có lý do — xem PointAdjustment. */
    DIEU_CHINH,
    /** Đợt 5: sinh viên mua vé tháng — xem PassPurchase. */
    MUA_VE
}
