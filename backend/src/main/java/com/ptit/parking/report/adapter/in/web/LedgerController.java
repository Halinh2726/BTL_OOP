package com.ptit.parking.report.adapter.in.web;

import com.ptit.parking.payment.domain.TransactionType;
import com.ptit.parking.report.application.usecase.LedgerUseCase;
import com.ptit.parking.report.application.usecase.LedgerUseCase.Criteria;
import com.ptit.parking.shared.web.CsvExport;
import jakarta.validation.constraints.Positive;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Đợt 4a — sổ giao dịch toàn hệ thống (quyền LEDGER_VIEW): xem có phân trang và xuất CSV. */
@RestController
@RequestMapping("/api/v1/manager/ledger")
class LedgerController {

    record LedgerPage(List<LedgerItem> items, boolean hasMore, Long nextBefore) {}

    private final LedgerUseCase ledger;
    private final ZoneId zone;

    LedgerController(LedgerUseCase ledger, Clock clock) {
        this.ledger = ledger;
        this.zone = clock.getZone();
    }

    @GetMapping
    LedgerPage page(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                    @RequestParam(required = false) TransactionType type,
                    @RequestParam(required = false) String studentCode,
                    @RequestParam(required = false) @Positive(message = "before phải > 0") Long before,
                    @RequestParam(defaultValue = "30") int limit) {
        LedgerUseCase.Page page = ledger.page(new Criteria(from, to, type, studentCode), before, limit);
        return new LedgerPage(page.items().stream().map(entry -> LedgerItem.of(entry, zone)).toList(),
                page.hasMore(), page.nextBefore());
    }

    /** File CSV (UTF-8 có BOM) — khoảng ngày bắt buộc, tối đa 93 ngày. */
    @GetMapping("/export")
    ResponseEntity<byte[]> export(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  @RequestParam(required = false) TransactionType type,
                                  @RequestParam(required = false) String studentCode) {
        Criteria criteria = new Criteria(from, to, type, studentCode);
        return CsvExport.response(new LedgerCsvReport(from, to, ledger.export(criteria), zone));
    }
}
