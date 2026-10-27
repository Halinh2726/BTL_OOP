package com.ptit.parking.payment.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.payment.application.port.out.InMemoryWalletRepository;
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import org.junit.jupiter.api.Test;

class GetMyWalletUseCaseTest {

    private final InMemoryWalletRepository wallets = new InMemoryWalletRepository();
    private final GetMyWalletUseCase getMyWallet = new GetMyWalletUseCase(wallets);

    @Test
    void traDungViCuaNguoiGoi() {
        wallets.insert(new Wallet(null, 3L, 50_000));          // sinh viên
        wallets.insert(new Wallet(null, 2L, 7_000));           // chủ tài khoản

        assertThat(getMyWallet.walletOf(3L).balance()).isEqualTo(50_000);
        assertThat(getMyWallet.walletOf(2L).balance()).isEqualTo(7_000);
    }

    @Test
    void nguoiKhongCoVi_baoNotFound() {
        assertThatThrownBy(() -> getMyWallet.walletOf(1L))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }
}