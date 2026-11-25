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

    /** Phí của MỘT lượt gửi có xét thời điểm xe vào (hỗ trợ tính phí qua đêm theo Mục 3.9). */
    default FeeQuote quoteSession(long vehicleId, Instant enteredAt, ZonedDateTime exitTime) {
        return quote(vehicleId, enteredAt, exitTime);
    }

    /** Kiểm tra đã tới giờ khóa không cho hoàn tất lấy xe trong ngày chưa (Mục 3.9). */
    default boolean isCheckoutLocked(ZonedDateTime exitTime) {
        return false;
    }

    /** Cấu hình giờ khóa / phí qua đêm đang áp dụng. */
    default ParkingPolicyConfig policyConfig() {
        return ParkingPolicyConfig.disabled();
    }

    /** Đợt 5 — mức điểm tối thiểu để xe này được VÀO lúc {@code at}. Mặc định: như nhau với mọi xe. */
    default long minimumFeeFor(long vehicleId, ZonedDateTime at) {
        return minimumFee();
    }
}
