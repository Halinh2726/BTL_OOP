package com.ptit.parking.parking.domain;

import java.time.LocalDate;

/**
 * Đợt 5 — kết quả tính phí cho MỘT xe: số điểm phải trả, và (nếu miễn phí nhờ vé tháng) vé còn hiệu lực tới ngày nào.
 */
public record FeeQuote(long points, LocalDate passValidUntil) {

    public FeeQuote {
        if (points < 0) {
            throw new IllegalArgumentException("Phí không được âm");
        }
    }

    public static FeeQuote charge(long points) {
        return new FeeQuote(points, null);
    }

    public static FeeQuote coveredByPass(LocalDate validUntil) {
        return new FeeQuote(0, validUntil);
    }

    /** Xe ra không phải trả gì — không ghi giao dịch thanh toán. */
    public boolean isFree() {
        return points == 0;
    }
}
