package com.ptit.parking.report.application.usecase;

import com.ptit.parking.report.application.port.out.TrafficQuery;
import com.ptit.parking.report.domain.TrafficProfile;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/**
 * Đợt 4a — thống kê lượt xe theo giờ để biết giờ cao điểm (xếp thêm người trực). Quản lý và chủ bãi xe cùng xem.
 * So sánh "trung bình N ngày" với "hôm nay" theo từng giờ.
 */
public class TrafficStatsUseCase {

    public static final Set<Integer> ALLOWED_DAYS = Set.of(7, 30, 90);
    public static final int PEAK_HOURS = 3;

    /** {@code today}: số lượt xe vào hôm nay theo từng giờ (24 phần tử). */
    public record Report(LocalDate from, LocalDate to, TrafficProfile profile, TrafficProfile today,
                         List<Integer> peakHours, long parkedNow) {}

    private final TrafficQuery traffic;
    private final Clock clock;

    public TrafficStatsUseCase(TrafficQuery traffic, Clock clock) {
        this.traffic = traffic;
        this.clock = clock;
    }

    public Report report(int days) {
        if (!ALLOWED_DAYS.contains(days)) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR);
        }
        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.minusDays(days - 1L);
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant todayStart = today.atStartOfDay(zone).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(zone).toInstant();

        TrafficProfile profile = TrafficProfile.of(days, traffic.entriesByHour(start, end, zone),
                traffic.exitsByHour(start, end, zone));
        TrafficProfile todayProfile = TrafficProfile.of(1, traffic.entriesByHour(todayStart, end, zone),
                traffic.exitsByHour(todayStart, end, zone));
        return new Report(from, today, profile, todayProfile, profile.peakHours(PEAK_HOURS), traffic.parkedNow());
    }
}
