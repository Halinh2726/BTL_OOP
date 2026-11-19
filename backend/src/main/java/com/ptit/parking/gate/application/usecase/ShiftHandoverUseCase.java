package com.ptit.parking.gate.application.usecase;

import com.ptit.parking.gate.application.port.out.GateActivityQuery;
import com.ptit.parking.gate.application.port.out.IncidentRepository;
import com.ptit.parking.gate.application.port.out.ShiftRepository;
import com.ptit.parking.gate.domain.CashOnHand;
import com.ptit.parking.gate.domain.GuardShift;
import com.ptit.parking.gate.domain.Incident;
import com.ptit.parking.gate.domain.ShiftSummary;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Đợt 2 — nhận ca / bàn giao ca.
 * <ul>
 *   <li><b>Kết thúc ca</b>: hệ thống tự tổng kết (số lượt vào, ra, xe còn trong bãi, sự cố chưa xử lý) và lưu thành
 *       biên bản — bảo vệ không tự gõ số liệu, nên không khai sai được. Chỉ đếm, không có điểm (R06).</li>
 *   <li><b>Nhận ca</b>: nếu có biên bản của bảo vệ khác chưa ai nhận, người nhận ca BẮT BUỘC đã xem đúng các biên bản đó
 *       (gửi lên đúng danh sách id) — xác nhận là đã đọc. Biên bản mới xuất hiện sau lúc xem → HANDOVER_PENDING, xem lại.</li>
 * </ul>
 */
public class ShiftHandoverUseCase {

    public static final int OPEN_INCIDENTS_SHOWN = 10;

    /**
     * Ca hiện tại (null nếu chưa nhận ca) + số liệu tạm tính tới bây giờ + các biên bản đang chờ nhận
     * + sự cố chưa xử lý. {@code names}: họ tên của các bảo vệ xuất hiện trong biên bản.
     */
    public record Overview(GuardShift current, ShiftSummary liveSummary, List<GuardShift> pendingHandovers,
                           List<Incident> openIncidents, Map<Long, String> names) {}

    private final ShiftRepository shifts;
    private final GateActivityQuery activity;
    private final IncidentRepository incidents;
    private final TransactionRunner transactions;
    private final Clock clock;

    public ShiftHandoverUseCase(ShiftRepository shifts, GateActivityQuery activity, IncidentRepository incidents,
                                TransactionRunner transactions, Clock clock) {
        this.shifts = shifts;
        this.activity = activity;
        this.incidents = incidents;
        this.transactions = transactions;
        this.clock = clock;
    }

    public Overview overview(long guardId) {
        Optional<GuardShift> current = shifts.findOpenByGuard(guardId);
        ShiftSummary live = current.map(shift -> summarize(guardId, shift.startedAt(), clock.instant())).orElse(null);
        List<GuardShift> pending = shifts.findPendingHandovers(guardId, false);
        List<Incident> open = incidents.findOpen(OPEN_INCIDENTS_SHOWN);
        Set<Long> people = new HashSet<>();
        pending.forEach(shift -> people.add(shift.guardId()));
        open.forEach(incident -> people.add(incident.reporterId()));
        return new Overview(current.orElse(null), live, pending, open, activity.namesOf(people));
    }

    /**
     * Nhận ca. Đang trong ca rồi thì trả lại ca đó (bấm hai lần không tạo hai ca).
     *
     * @param acknowledgedHandoverIds id các biên bản bảo vệ đã xem trên màn hình
     * @param openingCash             số tiền mặt bảo vệ khai đang cầm lúc nhận ca
     */
    public GuardShift start(long guardId, Set<Long> acknowledgedHandoverIds, CashOnHand openingCash) {
        return transactions.inTransaction(() -> {
            Optional<GuardShift> open = shifts.findOpenByGuard(guardId);
            if (open.isPresent()) {
                return open.get();
            }
            Instant now = clock.instant();
            List<GuardShift> pending = shifts.findPendingHandovers(guardId, true);
            Set<Long> pendingIds = pending.stream().map(GuardShift::id).collect(Collectors.toSet());
            if (!pendingIds.equals(acknowledgedHandoverIds == null ? Set.of() : acknowledgedHandoverIds)) {
                throw new DomainException(ErrorCode.HANDOVER_PENDING);
            }
            pending.forEach(handover -> {
                handover.receive(guardId, now);
                shifts.save(handover);
            });
            // Tiền ca trước bàn giao lại = tổng tiền bàn giao của các biên bản vừa nhận; không nhận biên bản nào -> null
            CashOnHand expected = pending.isEmpty() ? null : pending.stream()
                    .map(GuardShift::closingCash)
                    .reduce(CashOnHand.ZERO, CashOnHand::plus);
            // Hai request "Nhận ca" song song: UNIQUE uq_guard_shifts_open chặn ca thứ hai -> SHIFT_ALREADY_OPEN
            return shifts.save(GuardShift.start(guardId, openingCash, expected, now));
        });
    }

    /** Kết thúc ca và lập biên bản bàn giao: số liệu do hệ thống đếm, số tiền mặt bàn giao do bảo vệ khai. */
    public GuardShift end(long guardId, CashOnHand closingCash, String note) {
        return transactions.inTransaction(() -> {
            GuardShift shift = shifts.lockOpenByGuard(guardId)
                    .orElseThrow(() -> new DomainException(ErrorCode.NO_OPEN_SHIFT));
            Instant now = clock.instant();
            shift.end(summarize(guardId, shift.startedAt(), now), closingCash, note, now);
            return shifts.save(shift);
        });
    }

    private ShiftSummary summarize(long guardId, Instant from, Instant to) {
        return new ShiftSummary(
                Math.toIntExact(activity.countCheckIns(guardId, from, to)),
                Math.toIntExact(activity.countCheckOuts(guardId, from, to)),
                Math.toIntExact(activity.countParked()),
                Math.toIntExact(incidents.countOpen()));
    }
}
