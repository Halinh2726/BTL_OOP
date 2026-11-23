package com.ptit.parking.gate.application.usecase;

import com.ptit.parking.gate.application.port.out.ShiftRepository;
import com.ptit.parking.gate.domain.GuardShift;
import com.ptit.parking.parking.application.port.out.GateCashRepository;
import com.ptit.parking.parking.application.port.out.GateCashRepository.CashEntry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Mục 3.6 PDF — bảng kê tiền mặt bảo vệ đã thu tại chốt thuộc ca trực của CHÍNH mình.
 * Bảo vệ chỉ thấy dữ liệu ca/bản thân (Mục 2.1 PDF).
 */
public class ViewGuardCashSheetUseCase {

    private static final Duration FALLBACK_WINDOW = Duration.ofHours(12);

    public record CashSheet(
            Instant from,
            boolean inShift,
            Long shiftId,
            long openingCash,
            long totalCollected,
            long expectedClosingCash,
            List<CashEntry> entries
    ) {}

    private final ShiftRepository shifts;
    private final GateCashRepository gateCash;
    private final Clock clock;

    public ViewGuardCashSheetUseCase(ShiftRepository shifts, GateCashRepository gateCash, Clock clock) {
        this.shifts = shifts;
        this.gateCash = gateCash;
        this.clock = clock;
    }

    public CashSheet of(long guardId) {
        Optional<GuardShift> open = shifts.findOpenByGuard(guardId);
        Instant from = open.map(GuardShift::startedAt)
                .orElseGet(() -> clock.instant().minus(FALLBACK_WINDOW));
        boolean inShift = open.isPresent();
        Long shiftId = open.map(GuardShift::id).orElse(null);
        long openingCash = open.map(s -> s.openingCash().amount()).orElse(0L);

        List<CashEntry> entries = gateCash.findByGuardSince(guardId, from);
        long totalCollected = entries.stream().mapToLong(CashEntry::amount).sum();
        return new CashSheet(from, inShift, shiftId, openingCash, totalCollected,
                openingCash + totalCollected, entries);
    }
}
