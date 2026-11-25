package com.ptit.parking.manager.presentation.web;

import com.ptit.parking.payment.application.usecase.GetBankConfigUseCase;
import com.ptit.parking.payment.application.usecase.UpdateBankConfigUseCase;
import com.ptit.parking.shared.infrastructure.security.SessionUser;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quản lý cấu hình tài khoản ngân hàng nhận tiền VietQR (quyền CASH_DEPOSIT).
 * Cho phép Quản lý nhập/đổi tài khoản ngân hàng trực tiếp trên giao diện hoặc khôi phục về mặc định (.env).
 */
@RestController
@RequestMapping("/api/v1/manager/bank-config")
class BankConfigController {

    private final GetBankConfigUseCase getBankConfig;
    private final UpdateBankConfigUseCase updateBankConfig;
    private final ZoneId zone;

    BankConfigController(GetBankConfigUseCase getBankConfig, UpdateBankConfigUseCase updateBankConfig, Clock clock) {
        this.getBankConfig = getBankConfig;
        this.updateBankConfig = updateBankConfig;
        this.zone = clock.getZone();
    }

    @GetMapping
    BankConfigResponse current() {
        return BankConfigResponse.from(getBankConfig.current(), zone);
    }

    @PutMapping
    BankConfigResponse update(@AuthenticationPrincipal SessionUser manager,
                              @Valid @RequestBody UpdateBankConfigRequest body) {
        var updated = updateBankConfig.update(
                new UpdateBankConfigUseCase.Command(body.bankId(), body.accountNo(), body.accountName(), body.template()),
                manager.id());
        return BankConfigResponse.from(updated, zone);
    }

    @DeleteMapping
    BankConfigResponse resetToDefault() {
        return BankConfigResponse.from(updateBankConfig.resetToDefault(), zone);
    }
}
