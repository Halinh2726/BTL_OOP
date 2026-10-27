package com.ptit.parking.manager.adapter.in.web;

import com.ptit.parking.manager.application.usecase.DepositCashUseCase;
import com.ptit.parking.manager.application.usecase.LookupStudentUseCase;
import com.ptit.parking.manager.application.usecase.LookupStudentUseCase.StudentSummary;
import com.ptit.parking.shared.security.SessionUser;
import com.ptit.parking.shared.web.IdempotencyKeyHeader;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * QL01 — đổi tiền mặt sang điểm: API #16 tra cứu sinh viên, API #17 xác nhận đổi. Quyền CASH_DEPOSIT
 * (ma trận ProtectedEndpoint). Người thực hiện giao dịch là quản lý đang đăng nhập, lấy từ phiên.
 */
@RestController
@RequestMapping("/api/v1/manager")
class CashDepositController {

    private final LookupStudentUseCase lookupStudent;
    private final DepositCashUseCase depositCash;
    private final ZoneId zone;

    CashDepositController(LookupStudentUseCase lookupStudent, DepositCashUseCase depositCash, Clock clock) {
        this.lookupStudent = lookupStudent;
        this.depositCash = depositCash;
        this.zone = clock.getZone();
    }

    /** API #16. */
    @GetMapping("/students/{studentCode}")
    StudentSummary lookup(@PathVariable String studentCode) {
        return lookupStudent.lookup(studentCode);
    }

    /** API #17. */
    @PostMapping("/deposits")
    ResponseEntity<DepositResponse> deposit(@AuthenticationPrincipal SessionUser manager,
                                            @RequestHeader(value = IdempotencyKeyHeader.NAME, required = false) String key,
                                            @Valid @RequestBody DepositRequest body) {
        DepositCashUseCase.Result result = depositCash.deposit(new DepositCashUseCase.Command(
                manager.id(), IdempotencyKeyHeader.parse(key), body.studentCode(), body.cashAmount()));
        return IdempotencyKeyHeader.created(
                DepositResponse.from(result.deposit(), result.studentCode(), zone), result.replayed());
    }
}