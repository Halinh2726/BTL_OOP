package com.ptit.parking.gate.application.usecase;

import com.ptit.parking.gate.application.port.out.GateActivityQuery;
import com.ptit.parking.gate.domain.ParkedVehicle;
import com.ptit.parking.student.domain.PlateNumber;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Đợt 2 — bảo vệ xem các xe đang trong bãi: tổng số xe, tìm theo biển số, gửi lâu nhất lên đầu,
 * xe gửi quá 24 giờ được đánh dấu. Chỉ đọc; không có điểm của sinh viên (R06).
 */
public class ViewParkingLotUseCase {

    /** {@code total}: tổng số xe trong bãi (không phụ thuộc bộ lọc); {@code now}: mốc tính thời gian gửi. */
    public record LotView(long total, long overdue, List<ParkedVehicle> vehicles, Instant now) {}

    private final GateActivityQuery activity;
    private final Clock clock;

    public ViewParkingLotUseCase(GateActivityQuery activity, Clock clock) {
        this.activity = activity;
        this.clock = clock;
    }

    public LotView view(String rawFragment) {
        Instant now = clock.instant();
        String fragment = rawFragment == null ? "" : PlateNumber.normalize(rawFragment).replaceAll("[^A-Z0-9]", "");
        List<ParkedVehicle> all = activity.parkedVehicles();
        List<ParkedVehicle> shown = fragment.isEmpty()
                ? all
                : all.stream().filter(v -> v.plate().value().contains(fragment)).toList();
        long overdue = all.stream().filter(v -> v.isOverdue(now)).count();
        return new LotView(all.size(), overdue, shown, now);
    }
}
