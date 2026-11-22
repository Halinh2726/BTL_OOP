package com.ptit.parking.gate.adapter.in.web;

import com.ptit.parking.gate.application.usecase.ViewGuardCashSheetUseCase;
import com.ptit.parking.gate.application.usecase.ViewGuardCashSheetUseCase.CashSheet;
import com.ptit.parking.parking.application.port.out.GateCashRepository.CashEntry;
import com.ptit.parking.shared.security.SessionUser;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Mục 3.6 PDF — bảng kê tiền mặt thu tại chốt trong ca trực của chính bảo vệ đang đăng nhập. */
@RestController
@RequestMapping("/api/v1/guard/cash-sheet")
class GateCashSheetController {

    record CashEntryView(long collectionId, long sessionId, String plateNumber, String plateDisplay,
                         String vehicleTypeCode, String studentCode, String fullName,
                         long amount, boolean overnight, String collectedAt) {

        static CashEntryView of(CashEntry e, ZoneId zone) {
            return new CashEntryView(
                    e.collectionId(), e.sessionId(), e.plate().value(), e.plate().display(),
                    e.vehicleTypeCode(), e.studentCode(), e.fullName(),
                    e.amount(), e.overnight(), GateResponses.time(e.collectedAt(), zone));
        }
    }

    record CashSheetResponse(String from, boolean inShift, Long shiftId,
                             long openingCash, long totalCollected, long expectedClosingCash,
                             List<CashEntryView> entries) {}

    private final ViewGuardCashSheetUseCase cashSheet;
    private final ZoneId zone;

    GateCashSheetController(ViewGuardCashSheetUseCase cashSheet, Clock clock) {
        this.cashSheet = cashSheet;
        this.zone = clock.getZone();
    }

    @GetMapping
    CashSheetResponse current(@AuthenticationPrincipal SessionUser guard) {
        CashSheet sheet = cashSheet.of(guard.id());
        return new CashSheetResponse(
                GateResponses.time(sheet.from(), zone),
                sheet.inShift(),
                sheet.shiftId(),
                sheet.openingCash(),
                sheet.totalCollected(),
                sheet.expectedClosingCash(),
                sheet.entries().stream().map(e -> CashEntryView.of(e, zone)).toList());
    }
}
