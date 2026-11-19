package com.ptit.parking.gate.application.usecase;

import com.ptit.parking.gate.application.port.out.GuardRoster;
import com.ptit.parking.gate.application.port.out.GuardRoster.GuardInfo;
import com.ptit.parking.gate.application.port.out.ShiftAssignmentRepository;
import com.ptit.parking.gate.application.port.out.ShiftRepository;
import com.ptit.parking.gate.domain.GuardShift;
import com.ptit.parking.gate.domain.ShiftAssignment;
import com.ptit.parking.gate.domain.ShiftAssignment.Attendance;
import com.ptit.parking.gate.domain.ShiftSlot;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Đợt 4b — phân ca bảo vệ: quản lý xếp / gỡ ca theo tuần; mỗi ca được đối chiếu với giờ bảo vệ thực sự nhận ca
 * (đúng giờ, đi muộn, vắng). Bảo vệ xem lịch trực của chính mình.
 */
public class ShiftPlanUseCase {

    public static final int DAYS = 7;
    public static final int MY_PAST_DAYS = 7;
    public static final int MY_NEXT_DAYS = 14;

    /** Một ca đã xếp kèm tên bảo vệ và kết quả đối chiếu. */
    public record Planned(ShiftAssignment assignment, String guardName, Attendance attendance) {}

    public record Week(LocalDate from, LocalDate to, List<GuardInfo> guards, List<Planned> shifts) {}

    private final ShiftAssignmentRepository assignments;
    private final ShiftRepository shifts;
    private final GuardRoster roster;
    private final TransactionRunner transactions;
    private final Clock clock;

    public ShiftPlanUseCase(ShiftAssignmentRepository assignments, ShiftRepository shifts, GuardRoster roster,
                            TransactionRunner transactions, Clock clock) {
        this.assignments = assignments;
        this.shifts = shifts;
        this.roster = roster;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** 7 ngày bắt đầu từ {@code from} (mặc định hôm nay). */
    public Week week(LocalDate from) {
        LocalDate start = from == null ? LocalDate.now(clock) : from;
        LocalDate end = start.plusDays(DAYS - 1L);
        List<GuardInfo> guards = roster.all();
        return new Week(start, end, guards, planned(start, end, null, guards));
    }

    public Planned assign(long managerId, LocalDate date, ShiftSlot slot, long guardId) {
        GuardInfo guard = roster.find(guardId).orElseThrow(() -> new DomainException(ErrorCode.GUARD_NOT_FOUND));
        if (!guard.active()) {
            throw new DomainException(ErrorCode.GUARD_INACTIVE);
        }
        ShiftAssignment plan = ShiftAssignment.plan(date, slot, guardId, managerId, clock.instant(), clock.getZone());
        // Xếp trùng (cùng ngày, cùng ca, cùng bảo vệ): UNIQUE uq_shift_assignments -> SHIFT_ALREADY_ASSIGNED
        ShiftAssignment saved = transactions.inTransaction(() -> assignments.insert(plan));
        // Ca đang diễn ra có thể đã được bảo vệ nhận từ trước: đối chiếu ngay với giờ nhận ca thực tế
        ZoneId zone = clock.getZone();
        List<Instant> starts = shifts.findStartedBetween(saved.startsAt(zone).minus(ShiftAssignment.EARLY),
                saved.endsAt(zone), guardId).stream().map(GuardShift::startedAt).toList();
        return new Planned(saved, guard.fullName(), saved.attendance(starts, clock.instant(), zone));
    }

    public void remove(long assignmentId) {
        transactions.inTransaction(() -> {
            ShiftAssignment assignment = assignments.findById(assignmentId)
                    .orElseThrow(() -> new DomainException(ErrorCode.SHIFT_ASSIGNMENT_NOT_FOUND));
            assignment.requireRemovable(clock.instant(), clock.getZone());
            assignments.delete(assignmentId);
            return null;
        });
    }

    /** Lịch trực của bảo vệ: 7 ngày trước (đối chiếu) và 14 ngày tới. */
    public List<Planned> mySchedule(long guardId) {
        LocalDate today = LocalDate.now(clock);
        return planned(today.minusDays(MY_PAST_DAYS), today.plusDays(MY_NEXT_DAYS), guardId, roster.all());
    }

    private List<Planned> planned(LocalDate from, LocalDate to, Long guardId, List<GuardInfo> guards) {
        ZoneId zone = clock.getZone();
        Instant now = clock.instant();
        Map<Long, String> names = guards.stream().collect(Collectors.toMap(GuardInfo::id, GuardInfo::fullName));
        // Mọi lần nhận ca trong khoảng (ca đêm cuối kéo sang sáng hôm sau; nhận sớm tối đa 60 phút)
        Instant windowStart = ShiftSlot.SANG.startsAt(from, zone).minus(ShiftAssignment.EARLY).minusSeconds(6 * 3600L);
        Instant windowEnd = ShiftSlot.DEM.endsAt(to, zone);
        Map<Long, List<Instant>> starts = shifts.findStartedBetween(windowStart, windowEnd, guardId).stream()
                .collect(Collectors.groupingBy(GuardShift::guardId,
                        Collectors.mapping(GuardShift::startedAt, Collectors.toList())));
        return assignments.findBetween(from, to, guardId).stream()
                .sorted(Comparator.comparing(ShiftAssignment::workDate).thenComparing(ShiftAssignment::slot)
                        .thenComparing(a -> names.getOrDefault(a.guardId(), "")))
                .map(a -> new Planned(a, names.get(a.guardId()),
                        a.attendance(starts.getOrDefault(a.guardId(), List.of()), now, zone)))
                .toList();
    }
}
