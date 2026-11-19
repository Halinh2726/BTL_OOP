package com.ptit.parking.gate.application.port.out;

import com.ptit.parking.gate.domain.GuardShift;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Port: ca trực / biên bản bàn giao ca / dữ liệu chấm công (guard_shifts). */
public interface ShiftRepository {

    /** Ca đang trực của bảo vệ (nếu có). Chỉ đọc. */
    Optional<GuardShift> findOpenByGuard(long guardId);

    /** Như trên nhưng KHÓA dòng tới hết transaction — bấm "Kết thúc ca" hai lần cũng chỉ kết thúc một lần. */
    Optional<GuardShift> lockOpenByGuard(long guardId);

    /**
     * Các biên bản bàn giao CHƯA ai nhận, của bảo vệ KHÁC {@code excludingGuardId}, mới nhất trước.
     * {@code lock = true}: khóa các dòng tới hết transaction — hai bảo vệ cùng bấm "Nhận ca" thì người sau chờ.
     */
    List<GuardShift> findPendingHandovers(long excludingGuardId, boolean lock);

    /** Các ca gần nhất của MỘT bảo vệ, mới nhất trước — "lịch sử trực ca của tôi". */
    List<GuardShift> findByGuard(long guardId, int limit);

    /**
     * Chấm công: các ca BẮT ĐẦU trong [from, to), cũ nhất trước.
     *
     * @param guardId chỉ lấy ca của bảo vệ này; {@code null} = mọi bảo vệ
     */
    List<GuardShift> findStartedBetween(Instant from, Instant to, Long guardId);

    /** Ca mới (chưa có id) → thêm; đã có id → ghi số liệu kết thúc / người nhận. */
    GuardShift save(GuardShift shift);
}
