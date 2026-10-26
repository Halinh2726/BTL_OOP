package com.ptit.parking.payment.domain;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;

/**
 * Tỷ lệ quy đổi tiền ↔ điểm (system_config.cash_per_point), ví dụ 1 đồng = 1 điểm.
 * Value object (record bất biến): phép quy đổi nằm cạnh dữ liệu của nó, không rải ở service.
 */
public record ExchangeRate(int cashPerPoint) {

    public ExchangeRate {
        if (cashPerPoint <= 0) {
            throw new IllegalArgumentException("cashPerPoint phải > 0");
        }
    }

    /** QL01 – R02: số tiền phải > 0 và là bội số của tỷ lệ quy đổi. */
    public long pointsFor(long cashAmount) {
        if (cashAmount <= 0 || cashAmount % cashPerPoint != 0) {
            throw new DomainException(ErrorCode.INVALID_AMOUNT);
        }
        return cashAmount / cashPerPoint;
    }

    /** QL02: số tiền mặt quản lý trả cho chủ tài khoản khi rút {@code points} điểm. */
    public long cashFor(long points) {
        if (points <= 0) {
            throw new DomainException(ErrorCode.INVALID_AMOUNT);
        }
        return Math.multiplyExact(points, (long) cashPerPoint);
    }
}
