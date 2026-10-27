package com.ptit.parking.manager.adapter.in.web;

import com.ptit.parking.manager.application.usecase.GetOwnerWalletUseCase;
import com.ptit.parking.manager.application.usecase.GetOwnerWalletUseCase.OwnerWallet;
import com.ptit.parking.manager.application.usecase.WithdrawCashUseCase;
import com.ptit.parking.shared.security.SessionUser;
import com.ptit.parking.shared.web.IdempotencyKeyHeader;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * QL02 — rút điểm ra tiền mặt: API #18 xem ví chủ tài khoản, API #19 xác nhận rút. Quyền CASH_WITHDRAWAL
 * (ma trận ProtectedEndpoint). Người thực hiện giao dịch là quản lý đang đăng nhập, lấy từ phiên.
 */
@RestController
@RequestMapping("/api/v1/manager")
class CashWithdrawalController {

    private final GetOwnerWalletUseCase getOwnerWallet;
    private final WithdrawCashUseCase withdrawCash;
    private final ZoneId zone;

    CashWithdrawalController(GetOwnerWalletUseCase getOwnerWallet, WithdrawCashUseCase withdrawCash, Clock clock) {
        this.getOwnerWallet = getOwnerWallet;
        this.withdrawCash = withdrawCash;
        this.zone = clock.getZone();
    }

    /** API #18. */
    @GetMapping("/owner-wallet")
    OwnerWallet ownerWallet() {
        return getOwnerWallet.ownerWallet();
    }

    /** API #19. */
    @PostMapping("/withdrawals")
    ResponseEntity<WithdrawalResponse> withdraw(@AuthenticationPrincipal SessionUser manager,
                                                @RequestHeader(value = IdempotencyKeyHeader.NAME, required = false) String key,
                                                @Valid @RequestBody WithdrawRequest body) {
        WithdrawCashUseCase.Result result = withdrawCash.withdraw(new WithdrawCashUseCase.Command(
                manager.id(), IdempotencyKeyHeader.parse(key), body.points()));
        return IdempotencyKeyHeader.created(WithdrawalResponse.from(result.withdrawal(), zone), result.replayed());
    }
}