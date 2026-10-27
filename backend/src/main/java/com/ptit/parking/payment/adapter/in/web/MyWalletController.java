package com.ptit.parking.payment.adapter.in.web;

import com.ptit.parking.payment.application.usecase.GetMyWalletUseCase;
import com.ptit.parking.shared.security.SessionUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Ví của tôi" — quyền WALLET_VIEW_OWN (sinh viên, chủ tài khoản). Ví luôn là ví của người đang đăng nhập:
 * không có tham số nào để xem ví người khác.
 */
@RestController
@RequestMapping("/api/v1/me")
class MyWalletController {

    /** Response của API #7. */
    record WalletResponse(long balance) {}

    private final GetMyWalletUseCase getMyWallet;

    MyWalletController(GetMyWalletUseCase getMyWallet) {
        this.getMyWallet = getMyWallet;
    }

    /** API #7 — số điểm hiện có. */
    @GetMapping("/wallet")
    WalletResponse wallet(@AuthenticationPrincipal SessionUser user) {
        return new WalletResponse(getMyWallet.walletOf(user.id()).balance());
    }
}