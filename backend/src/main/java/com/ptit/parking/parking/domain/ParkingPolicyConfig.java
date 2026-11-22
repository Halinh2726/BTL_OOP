package com.ptit.parking.parking.domain;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Cấu hình giờ khóa lấy xe trong ngày và mức phí qua đêm (Mục 3.8, 3.9).
 * Mặc định: khóa lấy xe từ 20:00 giờ Việt Nam; lượt mở qua ngày tính 15.000 điểm tổng cộng.
 */
public record ParkingPolicyConfig(
        LocalTime checkoutCurfewTime,
        long overnightFeePoints,
        boolean curfewEnabled
) {

    public static final ParkingPolicyConfig DEFAULT =
            new ParkingPolicyConfig(LocalTime.of(20, 0), 15_000L, true);

    public ParkingPolicyConfig {
        Objects.requireNonNull(checkoutCurfewTime, "checkoutCurfewTime");
        if (overnightFeePoints <= 0) {
            throw new IllegalArgumentException("Phí qua đêm phải lớn hơn 0");
        }
    }

    public static ParkingPolicyConfig disabled() {
        return new ParkingPolicyConfig(LocalTime.of(20, 0), 15_000L, false);
    }

    /** Đã tới hoặc quá giờ khóa lấy xe trong ngày (theo giờ Việt Nam) khi tính năng khóa được bật. */
    public boolean isCurfewActive(ZonedDateTime exitTime, ZoneId zone) {
        if (!curfewEnabled) {
            return false;
        }
        LocalTime localExit = exitTime.withZoneSameInstant(zone).toLocalTime();
        return !localExit.isBefore(checkoutCurfewTime);
    }

    /** Lượt gửi được coi là qua đêm khi ngày lấy xe (giờ Việt Nam) sau ngày vào. */
    public boolean isOvernight(Instant enteredAt, ZonedDateTime exitTime, ZoneId zone) {
        return exitTime.withZoneSameInstant(zone).toLocalDate()
                .isAfter(enteredAt.atZone(zone).toLocalDate());
    }
}
