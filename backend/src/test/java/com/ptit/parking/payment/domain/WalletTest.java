package com.ptit.parking.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import org.junit.jupiter.api.Test;

class WalletTest {

    @Test
    void viMoiLuonCo0Diem() {
        Wallet wallet = Wallet.openFor(3L);

        assertThat(wallet.balance()).isZero();
        assertThat(wallet.id()).isNull();
    }

    @Test
    void congVaTruDiemHopLe() {
        Wallet wallet = new Wallet(1L, 3L, 0);

        wallet.credit(10_000);
        wallet.debit(3_000);

        assertThat(wallet.balance()).isEqualTo(7_000);
    }

    @Test
    void khongDuDiemThiBaoLoiVaSoDuGiuNguyen() {
        Wallet wallet = new Wallet(1L, 3L, 2_000);

        assertThatThrownBy(() -> wallet.debit(3_000))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_POINTS));
        assertThat(wallet.balance()).isEqualTo(2_000);   // R01: không bao giờ âm, không trừ một nửa
    }

    @Test
    void soDiemKhongDuongBiTuChoi() {
        Wallet wallet = new Wallet(1L, 3L, 5_000);

        assertThatThrownBy(() -> wallet.credit(0)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> wallet.debit(-500)).isInstanceOf(DomainException.class);
        assertThat(wallet.balance()).isEqualTo(5_000);
    }

    @Test
    void kiemTraDuMucPhiNhungKhongTru() {
        Wallet wallet = new Wallet(1L, 3L, 3_000);

        assertThat(wallet.hasAtLeast(3_000)).isTrue();
        assertThat(wallet.hasAtLeast(3_001)).isFalse();
        assertThat(wallet.balance()).isEqualTo(3_000);
    }

    @Test
    void khongTheTaoViSoDuAm() {
        assertThatThrownBy(() -> new Wallet(1L, 3L, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
