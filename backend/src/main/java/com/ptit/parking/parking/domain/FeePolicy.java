package com.ptit.parking.parking.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;

/** Chính sách tính phí gửi xe (Strategy). Vé tháng có thể bọc chính sách này qua {@link #quote}. */
public interface FeePolicy {

    /** R03 — phí theo thời lượng gửi xe thực tế. */
    long feeFor(Duration parkedFor);

    /** R04 — số điểm tối thiểu để xe được vào bãi. */
    long minimumFee();

    /** Báo phí cho một lượt gửi. Giờ ra cũng dùng để xác định hiệu lực vé tháng. */
    default FeeQuote quote(long vehicleId, Instant enteredAt, ZonedDateTime exitTime) {
        return FeeQuote.charge(feeFor(Duration.between(enteredAt, exitTime.toInstant())));
    }

    /** Mức điểm tối thiểu để một xe cụ thể được vào bãi. */
    default long minimumFeeFor(long vehicleId, ZonedDateTime at) {
        return minimumFee();
    }
}
