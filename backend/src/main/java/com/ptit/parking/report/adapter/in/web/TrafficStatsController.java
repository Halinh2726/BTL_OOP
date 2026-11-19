package com.ptit.parking.report.adapter.in.web;

import com.ptit.parking.report.application.usecase.TrafficStatsUseCase;
import com.ptit.parking.report.domain.TrafficProfile;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Đợt 4a — thống kê lượt xe theo giờ (quyền TRAFFIC_STATS: quản lý và chủ bãi xe). */
@RestController
class TrafficStatsController {

    /** Một giờ trong ngày: trung bình N ngày (làm tròn 1 chữ số) và số thật của hôm nay. */
    record HourView(int hour, double averageEntries, double averageExits, long todayEntries, long todayExits) {}

    record TrafficResponse(String from, String to, int days, List<HourView> hours, long totalEntries,
                           double averageEntriesPerDay, List<Integer> peakHours, long parkedNow) {}

    private final TrafficStatsUseCase stats;

    TrafficStatsController(TrafficStatsUseCase stats) {
        this.stats = stats;
    }

    @GetMapping("/api/v1/stats/traffic")
    TrafficResponse traffic(@RequestParam(defaultValue = "30") int days) {
        TrafficStatsUseCase.Report report = stats.report(days);
        TrafficProfile profile = report.profile();
        TrafficProfile today = report.today();
        List<HourView> hours = IntStream.range(0, TrafficProfile.HOURS)
                .mapToObj(h -> new HourView(h, round(profile.averageEntriesAt(h)), round(profile.averageExitsAt(h)),
                        today.entries().get(h), today.exits().get(h)))
                .toList();
        return new TrafficResponse(report.from().toString(), report.to().toString(), profile.days(), hours,
                profile.totalEntries(), round(profile.averageEntriesPerDay()), report.peakHours(), report.parkedNow());
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
