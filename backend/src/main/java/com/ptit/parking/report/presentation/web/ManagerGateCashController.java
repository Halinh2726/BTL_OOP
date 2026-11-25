package com.ptit.parking.report.presentation.web;

import com.ptit.parking.parking.application.port.out.GateCashRepository.CashEntry;
import com.ptit.parking.report.application.usecase.ViewManagerGateCashUseCase;
import com.ptit.parking.report.application.usecase.ViewManagerGateCashUseCase.GuardTotal;
import com.ptit.parking.report.application.usecase.ViewManagerGateCashUseCase.Report;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Mục 3.6 PDF — Quản lý xem tiền mặt bảo vệ đã thu tại chốt theo ngày (quyền CASH_RECONCILE). */
@RestController
@RequestMapping("/api/v1/manager/gate-cash")
class ManagerGateCashController {

    record GuardSummaryView(long guardId, String guardName, int count, long totalAmount) {
        static GuardSummaryView of(GuardTotal t) {
            return new GuardSummaryView(t.guardId(), t.guardName(), t.count(), t.totalAmount());
        }
    }

    record GateCashEntryView(long collectionId, long sessionId, String plateNumber, String plateDisplay,
                             String studentCode, String fullName, String guardName, Long shiftId,
                             long amount, boolean overnight, String collectedAt) {

        static GateCashEntryView of(CashEntry e, ZoneId zone) {
            return new GateCashEntryView(
                    e.collectionId(), e.sessionId(), e.plate().value(), e.plate().display(),
                    e.studentCode(), e.fullName(), e.guardName(), e.shiftId(),
                    e.amount(), e.overnight(), LedgerItem.iso(e.collectedAt(), zone));
        }
    }

    record GateCashReportResponse(String date, long totalAmount, int totalCount,
                                  List<GuardSummaryView> byGuard, List<GateCashEntryView> entries) {}

    private final ViewManagerGateCashUseCase gateCash;
    private final Clock clock;
    private final ZoneId zone;

    ManagerGateCashController(ViewManagerGateCashUseCase gateCash, Clock clock) {
        this.gateCash = gateCash;
        this.clock = clock;
        this.zone = clock.getZone();
    }

    @GetMapping
    GateCashReportResponse onDate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now(clock);
        Report report = gateCash.onDate(target);
        return new GateCashReportResponse(
                report.date().toString(),
                report.totalAmount(),
                report.totalCount(),
                report.byGuard().stream().map(GuardSummaryView::of).toList(),
                report.entries().stream().map(e -> GateCashEntryView.of(e, zone)).toList());
    }
}
