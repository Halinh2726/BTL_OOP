package com.ptit.parking.gate.application.usecase;

import com.ptit.parking.gate.application.port.out.GateActivityQuery;
import com.ptit.parking.gate.application.port.out.ShiftRepository;
import com.ptit.parking.gate.domain.GuardShift;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Chấm công bảo vệ — dữ liệu lấy thẳng từ các ca trực (nhận ca → kết thúc ca), không ai phải tự ghi giờ:
 * <ul>
 *   <li>Bảo vệ xem lịch sử trực ca của CHÍNH MÌNH.</li>
 *   <li>Quản lý xem bảng chấm công theo khoảng ngày (giờ Việt Nam): mỗi bảo vệ bao nhiêu ca, tổng giờ trực,
 *       số lượt vào/ra, số ca khai tiền nhận ca lệch với tiền ca trước bàn giao.</li>
 * </ul>
 */
public class ShiftAttendanceUseCase {

    public static final int MY_HISTORY_LIMIT = 60;
    /** Một lần xem tối đa 93 ngày (một quý) cho nhẹ truy vấn. */
    public static final long MAX_RANGE_DAYS = 93;

    /** Tổng hợp của một bảo vệ trong khoảng ngày. */
    public record GuardTotals(long guardId, String guardName, int shifts, long workedMinutes, int checkIns,
                              int checkOuts, int cashMismatches) {}

    public record Timesheet(List<GuardTotals> totals, List<GuardShift> shifts, Map<Long, String> names, Instant now) {}

    public record MyHistory(List<GuardShift> shifts, Map<Long, String> names, Instant now) {}

    private final ShiftRepository shifts;
    private final GateActivityQuery activity;
    private final Clock clock;

    public ShiftAttendanceUseCase(ShiftRepository shifts, GateActivityQuery activity, Clock clock) {
        this.shifts = shifts;
        this.activity = activity;
        this.clock = clock;
    }

    public MyHistory myHistory(long guardId) {
        List<GuardShift> mine = shifts.findByGuard(guardId, MY_HISTORY_LIMIT);
        return new MyHistory(mine, namesIn(mine), clock.instant());
    }

    /** @param guardId {@code null} = mọi bảo vệ */
    public Timesheet timesheet(LocalDate from, LocalDate to, Long guardId) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR);
        }
        Instant start = from.atStartOfDay(clock.getZone()).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        Instant now = clock.instant();
        List<GuardShift> rows = shifts.findStartedBetween(start, end, guardId);
        Map<Long, String> names = namesIn(rows);

        Map<Long, List<GuardShift>> byGuard = new LinkedHashMap<>();
        rows.forEach(shift -> byGuard.computeIfAbsent(shift.guardId(), id -> new ArrayList<>()).add(shift));
        List<GuardTotals> totals = byGuard.entrySet().stream()
                .map(entry -> totalsOf(entry.getKey(), names.get(entry.getKey()), entry.getValue(), now))
                .sorted((a, b) -> String.valueOf(a.guardName()).compareTo(String.valueOf(b.guardName())))
                .toList();
        return new Timesheet(totals, rows, names, now);
    }

    private static GuardTotals totalsOf(long guardId, String name, List<GuardShift> shifts, Instant now) {
        long minutes = 0;
        int checkIns = 0;
        int checkOuts = 0;
        int mismatches = 0;
        for (GuardShift shift : shifts) {
            minutes += shift.worked(now).toMinutes();
            if (shift.summary() != null) {
                checkIns += shift.summary().checkIns();
                checkOuts += shift.summary().checkOuts();
            }
            if (shift.openingDiscrepancy().filter(difference -> difference != 0).isPresent()) {
                mismatches++;
            }
        }
        return new GuardTotals(guardId, name, shifts.size(), minutes, checkIns, checkOuts, mismatches);
    }

    private Map<Long, String> namesIn(List<GuardShift> rows) {
        Set<Long> people = new HashSet<>();
        rows.forEach(shift -> {
            people.add(shift.guardId());
            if (shift.receivedBy() != null) {
                people.add(shift.receivedBy());
            }
        });
        return activity.namesOf(people);
    }
}
