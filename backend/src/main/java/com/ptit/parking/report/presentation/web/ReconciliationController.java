package com.ptit.parking.report.presentation.web;

import com.ptit.parking.report.application.port.out.ReconciliationRepository.Closed;
import com.ptit.parking.report.application.usecase.ReconciliationUseCase;
import com.ptit.parking.report.domain.CashPosition;
import com.ptit.parking.report.domain.DailyReconciliation;
import com.ptit.parking.shared.infrastructure.security.SessionUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Đợt 4a — đối soát tiền mặt cuối ngày (quyền CASH_RECONCILE). */
@RestController
@RequestMapping("/api/v1/manager/reconciliations")
class ReconciliationController {

    record CloseRequest(
            @NotNull(message = "Vui lòng chọn ngày") LocalDate date,
            @NotNull(message = "Vui lòng nhập số tiền đếm được")
            @PositiveOrZero(message = "Số tiền không được âm")
            @Max(value = DailyReconciliation.MAX_CASH, message = "Số tiền quá lớn") Long countedCash,
            @Size(max = DailyReconciliation.MAX_NOTE, message = "Giải trình tối đa 500 ký tự") String note) {}

    record ClosedView(String date, long cashIn, long cashOut, long expectedCash, long countedCash, long discrepancy,
                      String outcome, boolean pointsBalanced, String note, String closedByName, String closedAt) {}

    record DayView(String date, long openingCash, long cashIn, long cashOut, long expectedCash, long deposits,
                   long withdrawals, long payments, long revenue, boolean pointsBalanced, ClosedView closed) {}

    private final ReconciliationUseCase reconciliations;
    private final ZoneId zone;

    ReconciliationController(ReconciliationUseCase reconciliations, Clock clock) {
        this.reconciliations = reconciliations;
        this.zone = clock.getZone();
    }

    @GetMapping
    List<ClosedView> history() {
        return reconciliations.history().stream().map(this::view).toList();
    }

    @GetMapping("/{date}")
    DayView day(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        ReconciliationUseCase.Day day = reconciliations.day(date);
        CashPosition p = day.position();
        return new DayView(p.date().toString(), p.openingCash(), p.cashIn(), p.cashOut(), p.expectedCash(),
                p.deposits(), p.withdrawals(), p.payments(), p.revenue(), p.pointsBalanced(),
                day.closed().map(this::view).orElse(null));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ClosedView close(@AuthenticationPrincipal SessionUser manager, @Valid @RequestBody CloseRequest body) {
        return view(reconciliations.close(new ReconciliationUseCase.Command(manager.id(), body.date(),
                body.countedCash(), body.note())));
    }

    private ClosedView view(Closed closed) {
        DailyReconciliation r = closed.reconciliation();
        return new ClosedView(r.businessDate().toString(), r.cashIn(), r.cashOut(), r.expectedCash(), r.countedCash(),
                r.discrepancy(), r.outcome().name(), r.pointsBalanced(), r.note(), closed.closedByName(),
                LedgerItem.iso(r.closedAt(), zone));
    }
}
