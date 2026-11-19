package com.ptit.parking.report.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Value Object: tình hình tiền mặt và điểm của MỘT ngày (giờ Việt Nam), tính từ sổ giao dịch.
 * <ul>
 *   <li>{@code openingCash}: tiền mặt quản lý phải giữ lúc đầu ngày = tổng (đổi điểm − rút điểm) trước ngày đó (R13).</li>
 *   <li>{@link #expectedCash()}: tiền mặt phải có lúc cuối ngày = đầu ngày + vào − ra.</li>
 *   <li>{@code pointsBalanced}: R12 — tổng điểm trong mọi ví = điểm đã phát hành − điểm đã rút.</li>
 * </ul>
 */
public record CashPosition(LocalDate date, long openingCash, long cashIn, long cashOut, long deposits, long withdrawals,
                           long payments, long revenue, boolean pointsBalanced) {

    public CashPosition {
        Objects.requireNonNull(date, "date");
        if (cashIn < 0 || cashOut < 0 || deposits < 0 || withdrawals < 0 || payments < 0 || revenue < 0) {
            throw new IllegalArgumentException("Số liệu trong ngày không được âm");
        }
    }

    /** R13 tại cuối ngày. */
    public long expectedCash() {
        return openingCash + cashIn - cashOut;
    }
}
