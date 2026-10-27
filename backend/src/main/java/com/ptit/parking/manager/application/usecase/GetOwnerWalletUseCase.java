package com.ptit.parking.manager.application.usecase;

import com.ptit.parking.manager.application.port.out.OwnerAccountDirectory;
import com.ptit.parking.manager.application.port.out.OwnerAccountDirectory.OwnerAccount;
import com.ptit.parking.payment.application.port.out.WalletRepository;

/** QL02 bước 1 (Đặc tả API #18) — quản lý xem chủ tài khoản đang có bao nhiêu điểm trước khi rút. Chỉ đọc. */
public class GetOwnerWalletUseCase {

    public record OwnerWallet(String fullName, long balance) {}

    private final OwnerAccountDirectory owners;
    private final WalletRepository wallets;

    public GetOwnerWalletUseCase(OwnerAccountDirectory owners, WalletRepository wallets) {
        this.owners = owners;
        this.wallets = wallets;
    }

    public OwnerWallet ownerWallet() {
        OwnerAccount owner = owners.ownerAccount();
        long balance = wallets.findByUserId(owner.userId()).orElseThrow().balance();
        return new OwnerWallet(owner.fullName(), balance);
    }
}
