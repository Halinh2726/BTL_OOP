package com.ptit.parking.parking.domain;

import com.ptit.parking.catalog.domain.FeeRule;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * Chính sách tính phí theo khung giờ cố định và cộng dồn qua đêm (theo giờ Việt Nam):
 * <ul>
 *   <li>Trước 18:00 trong ngày: mức giá ban ngày (mặc định 3.000 điểm).</li>
 *   <li>Từ 18:00 đến trước 22:00 trong ngày: mức giá buổi tối (mặc định 5.000 điểm).</li>
 *   <li>Từ 22:00 trở đi / để qua đêm sang ngày hôm sau: mỗi đêm cộng mức phí qua đêm (mặc định 15.000 điểm)
 *       cộng dồn với mức phí khung giờ của ngày lấy xe.
 *       Ví dụ gửi Thứ 2, lấy lúc 19:00 Thứ 4 = 15.000 + 15.000 + 5.000 = 35.000 điểm.</li>
 * </ul>
 */
public final class TimeSlotFeePolicy implements FeePolicy {

    public static final LocalTime EVENING_START = LocalTime.of(18, 0);
    public static final LocalTime OVERNIGHT_START = LocalTime.of(22, 0);

    private final long dayFee;
    private final long eveningFee;
    private final long overnightFee;
    private final ZoneId zone;

    public TimeSlotFeePolicy(List<FeeRule> rules, ZoneId zone) {
        List<FeeRule> sorted = FeeRule.validateSchedule(rules);
        this.zone = Objects.requireNonNull(zone, "zone");
        this.dayFee = findPrice(sorted, FeeRule.DAY_SLOT_MINUTES, sorted.getFirst().feePoints());
        this.eveningFee = findPrice(sorted, FeeRule.EVENING_SLOT_MINUTES,
                sorted.size() >= 2 ? sorted.get(1).feePoints() : this.dayFee);
        this.overnightFee = findPrice(sorted, FeeRule.OVERNIGHT_SLOT_MINUTES, sorted.getLast().feePoints());
    }

    private static long findPrice(List<FeeRule> sorted, int slotMinutes, long fallback) {
        for (FeeRule rule : sorted) {
            if (rule.upToMinutes() == slotMinutes) {
                return rule.feePoints();
            }
        }
        return fallback;
    }

    @Override
    public long feeFor(Duration parkedFor) {
        if (parkedFor.isNegative()) {
            throw new IllegalArgumentException("Giờ ra trước giờ vào");
        }
        return dayFee;
    }

    @Override
    public long minimumFee() {
        return dayFee;
    }

    @Override
    public FeeQuote quote(long vehicleId, Instant enteredAt, ZonedDateTime exitTime) {
        return quoteSession(vehicleId, enteredAt, exitTime);
    }

    @Override
    public FeeQuote quoteSession(long vehicleId, Instant enteredAt, ZonedDateTime exitTime) {
        ZonedDateTime entryLocal = enteredAt.atZone(zone);
        ZonedDateTime exitLocal = exitTime.withZoneSameInstant(zone);
        if (exitLocal.isBefore(entryLocal)) {
            throw new IllegalArgumentException("Giờ ra trước giờ vào");
        }
        LocalDate entryDate = entryLocal.toLocalDate();
        LocalDate exitDate = exitLocal.toLocalDate();
        LocalTime exitClock = exitLocal.toLocalTime();

        long nights = Math.max(0L, ChronoUnit.DAYS.between(entryDate, exitDate));
        long exitDayFee;
        if (exitClock.isBefore(EVENING_START)) {
            exitDayFee = dayFee;
        } else if (exitClock.isBefore(OVERNIGHT_START)) {
            exitDayFee = eveningFee;
        } else {
            exitDayFee = overnightFee;
        }

        long total = Math.addExact(Math.multiplyExact(nights, overnightFee), exitDayFee);
        boolean overnight = nights > 0 || !exitClock.isBefore(OVERNIGHT_START);
        return overnight ? FeeQuote.overnightCharge(total) : FeeQuote.charge(total);
    }
}
