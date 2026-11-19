package com.ptit.parking.gate.application.usecase;

import com.ptit.parking.gate.application.port.out.GateActivityQuery;
import com.ptit.parking.gate.application.port.out.GateActivityQuery.ActivityEntry;
import com.ptit.parking.gate.application.port.out.ShiftRepository;
import com.ptit.parking.gate.domain.GuardShift;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Đợt 2 — "lịch sử ca trực của tôi": các xe CHÍNH bảo vệ này đã cho vào / cho ra, để đối chiếu khi sinh viên thắc mắc.
 * Đang trong ca → tính từ lúc nhận ca; chưa nhận ca → 12 giờ gần nhất (bằng thời hạn tối đa của một phiên đăng nhập).
 */
public class ViewMyActivityUseCase {

    public static final Duration WITHOUT_SHIFT = Duration.ofHours(12);
    public static final int MAX_ITEMS = 200;

    public record MyActivity(Instant from, boolean inShift, long checkIns, long checkOuts, List<ActivityEntry> entries) {}

    private final GateActivityQuery activity;
    private final ShiftRepository shifts;
    private final Clock clock;

    public ViewMyActivityUseCase(GateActivityQuery activity, ShiftRepository shifts, Clock clock) {
        this.activity = activity;
        this.shifts = shifts;
        this.clock = clock;
    }

    public MyActivity of(long guardId) {
        Instant now = clock.instant();
        Optional<GuardShift> shift = shifts.findOpenByGuard(guardId);
        Instant from = shift.map(GuardShift::startedAt).orElse(now.minus(WITHOUT_SHIFT));
        return new MyActivity(from, shift.isPresent(),
                activity.countCheckIns(guardId, from, now), activity.countCheckOuts(guardId, from, now),
                activity.activityOf(guardId, from, MAX_ITEMS));
    }
}
