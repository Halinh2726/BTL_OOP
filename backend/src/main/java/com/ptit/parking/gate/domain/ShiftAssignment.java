package com.ptit.parking.gate.domain;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/**
 * Một lần quản lý xếp một bảo vệ vào một ca của một ngày. Tự trả lời: còn xếp / gỡ được không, và bảo vệ có đi làm
 * đúng giờ không ({@link #attendance}) — đối chiếu với các ca bảo vệ thực sự đã nhận (guard_shifts).
 */
public final class ShiftAssignment {

    /** Nhận ca muộn quá 15 phút tính là đi muộn. */
    public static final Duration GRACE = Duration.ofMinutes(15);
    /** Nhận ca sớm tối đa 60 phút vẫn tính cho ca này. */
    public static final Duration EARLY = Duration.ofMinutes(60);

    /** Kết quả đối chiếu: {@code actualStart} null khi chưa nhận ca; {@code lateMinutes} chỉ có nghĩa khi DI_MUON. */
    public record Attendance(AttendanceStatus status, Instant actualStart, long lateMinutes) {}

    private final Long id;
    private final LocalDate workDate;
    private final ShiftSlot slot;
    private final long guardId;
    private final long assignedBy;
    private final Instant assignedAt;

    private ShiftAssignment(Long id, LocalDate workDate, ShiftSlot slot, long guardId, long assignedBy, Instant assignedAt) {
        this.id = id;
        this.workDate = Objects.requireNonNull(workDate, "workDate");
        this.slot = Objects.requireNonNull(slot, "slot");
        this.guardId = guardId;
        this.assignedBy = assignedBy;
        this.assignedAt = Objects.requireNonNull(assignedAt, "assignedAt");
    }

    /** Quản lý xếp ca: không xếp được ca đã kết thúc. */
    public static ShiftAssignment plan(LocalDate workDate, ShiftSlot slot, long guardId, long managerId, Instant now,
                                       ZoneId zone) {
        if (!now.isBefore(slot.endsAt(workDate, zone))) {
            throw new DomainException(ErrorCode.SHIFT_SLOT_PASSED);
        }
        return new ShiftAssignment(null, workDate, slot, guardId, managerId, now.truncatedTo(ChronoUnit.MICROS));
    }

    public static ShiftAssignment restore(long id, LocalDate workDate, ShiftSlot slot, long guardId, long assignedBy,
                                          Instant assignedAt) {
        return new ShiftAssignment(id, workDate, slot, guardId, assignedBy, assignedAt);
    }

    /** Gỡ ca: chỉ khi ca CHƯA bắt đầu (ca đã bắt đầu là dữ liệu chấm công, không xóa). */
    public void requireRemovable(Instant now, ZoneId zone) {
        if (!now.isBefore(startsAt(zone))) {
            throw new DomainException(ErrorCode.SHIFT_SLOT_PASSED);
        }
    }

    /**
     * Đối chiếu với giờ NHẬN CA thực tế của chính bảo vệ này: lấy lần nhận ca sớm nhất trong khoảng
     * [giờ bắt đầu − 60 phút, giờ kết thúc).
     *
     * @param guardShiftStarts các thời điểm bảo vệ này đã bấm "Nhận ca"
     */
    public Attendance attendance(Collection<Instant> guardShiftStarts, Instant now, ZoneId zone) {
        Instant start = startsAt(zone);
        Instant end = endsAt(zone);
        Optional<Instant> actual = guardShiftStarts.stream()
                .filter(at -> !at.isBefore(start.minus(EARLY)) && at.isBefore(end))
                .min(Comparator.naturalOrder());
        if (actual.isPresent()) {
            long late = Math.max(0, Duration.between(start, actual.get()).toMinutes());
            AttendanceStatus status = late > GRACE.toMinutes() ? AttendanceStatus.DI_MUON : AttendanceStatus.DUNG_GIO;
            return new Attendance(status, actual.get(), late);
        }
        if (now.isBefore(start)) {
            return new Attendance(AttendanceStatus.SAP_TOI, null, 0);
        }
        return new Attendance(now.isBefore(end) ? AttendanceStatus.CHUA_NHAN_CA : AttendanceStatus.VANG, null, 0);
    }

    public Instant startsAt(ZoneId zone) {
        return slot.startsAt(workDate, zone);
    }

    public Instant endsAt(ZoneId zone) {
        return slot.endsAt(workDate, zone);
    }

    public ShiftAssignment withId(long newId) {
        return new ShiftAssignment(newId, workDate, slot, guardId, assignedBy, assignedAt);
    }

    public Long id() { return id; }

    public LocalDate workDate() { return workDate; }

    public ShiftSlot slot() { return slot; }

    public long guardId() { return guardId; }

    public long assignedBy() { return assignedBy; }

    public Instant assignedAt() { return assignedAt; }
}
