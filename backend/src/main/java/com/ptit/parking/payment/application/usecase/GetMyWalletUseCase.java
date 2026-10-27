package com.ptit.parking.payment.application.usecase;

import com.ptit.parking.payment.application.port.out.WalletRepository;
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;

/**
 * SV03, CT01 (Đặc tả API #7) — xem số điểm hiện có của CHÍNH MÌNH. Dùng chung cho sinh viên và chủ tài khoản:
 * cả hai đều là "người có ví" (quyền WALLET_VIEW_OWN); use case không cần biết người gọi là vai trò nào.
 */
public class GetMyWalletUseCase {

    private final WalletRepository wallets;

    public GetMyWalletUseCase(WalletRepository wallets) {
        this.wallets = wallets;
    }

    /** Chỉ đọc, không khóa — số dư hiển thị, không dùng để cộng/trừ. */
    public Wallet walletOf(long userId) {
        return wallets.findByUserId(userId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }
}