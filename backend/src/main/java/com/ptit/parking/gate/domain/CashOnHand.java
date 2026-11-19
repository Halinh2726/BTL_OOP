package com.ptit.parking.gate.domain;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;

/**
 * Số tiền mặt bảo vệ khai báo đang cầm khi nhận / bàn giao ca (value object, đơn vị đồng).
 * Chỉ là con số bảo vệ TỰ KHAI để quản lý đối chiếu — không phải doanh thu, không liên quan điểm (R06).
 * Giới hạn 0–50.000.000 đ để chặn gõ nhầm thêm số 0.
 */
public record CashOnHand(long amount) {

    public static final long MAX = 50_000_000L;
    public static final CashOnHand ZERO = new CashOnHand(0);

    public CashOnHand {
        if (amount < 0 || amount > MAX) {
            throw new DomainException(ErrorCode.INVALID_AMOUNT);
        }
    }

    public CashOnHand plus(CashOnHand other) {
        return new CashOnHand(amount + other.amount);
    }

    /** Số dương: khai nhận NHIỀU hơn ca trước bàn giao; số âm: khai ít hơn. */
    public long minus(CashOnHand other) {
        return amount - other.amount;
    }
}
