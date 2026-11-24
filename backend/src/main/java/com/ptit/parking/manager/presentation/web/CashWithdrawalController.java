package com.ptit.parking.manager.presentation.web;

import com.ptit.parking.manager.application.usecase.GetOwnerWalletUseCase;
import com.ptit.parking.manager.application.usecase.GetOwnerWalletUseCase.OwnerWallet;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API #18 — quản lý xem ví chủ bãi xe (quyền CASH_WITHDRAWAL).
 * <p>Đợt 3: API #19 "quản lý tự rút điểm" ĐÃ BỎ. Quản lý chỉ còn trả tiền theo yêu cầu rút do chủ bãi xe tạo
 * (WithdrawalProcessingController) — người tạo yêu cầu và người trả tiền là hai người khác nhau (R18).
 */
@RestController
@RequestMapping("/api/v1/manager")
class CashWithdrawalController {

    private final GetOwnerWalletUseCase getOwnerWallet;

    CashWithdrawalController(GetOwnerWalletUseCase getOwnerWallet) {
        this.getOwnerWallet = getOwnerWallet;
    }

    /** API #18. */
    @GetMapping("/owner-wallet")
    OwnerWallet ownerWallet() {
        return getOwnerWallet.ownerWallet();
    }
}
