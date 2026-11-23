package com.ptit.parking.parking.domain;

import java.time.LocalDate;

/**
 * Đợt 5 & Mục 3.9 — kết quả tính phí cho MỘT xe: số điểm phải trả, (nếu miễn phí nhờ vé tháng) vé còn
 * hiệu lực tới ngày nào, và {@code overnight} = true nếu áp dụng mức phí qua đêm (15.000 điểm tổng cộng).
 */
public record FeeQuote(long points, LocalDate passValidUntil, boolean overnight) {

    public FeeQuote {
        if (points < 0) {
            throw new IllegalArgumentException("Phí không được âm");
        }
    }

    public FeeQuote(long points, LocalDate passValidUntil) {
        this(points, passValidUntil, false);
    }

    public static FeeQuote charge(long points) {
        return new FeeQuote(points, null, false);
    }

    public static FeeQuote overnightCharge(long points) {
        return new FeeQuote(points, null, true);
    }

    public static FeeQuote coveredByPass(LocalDate validUntil) {
        return new FeeQuote(0, validUntil, false);
    }

    /** Xe ra không phải trả gì — không ghi giao dịch thanh toán. */
    public boolean isFree() {
        return points == 0;
    }
}
