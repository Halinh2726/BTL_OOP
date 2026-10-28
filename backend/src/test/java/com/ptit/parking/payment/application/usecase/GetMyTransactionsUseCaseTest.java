package com.ptit.parking.payment.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.ptit.parking.payment.application.port.out.InMemoryPointTransactionRepository;
import com.ptit.parking.payment.application.port.out.InMemoryWalletRepository;
import com.ptit.parking.payment.application.usecase.GetMyTransactionsUseCase.Page;
import com.ptit.parking.payment.domain.CashDeposit;
import com.ptit.parking.payment.domain.CashWithdrawal;
import com.ptit.parking.payment.domain.ExchangeRate;
import com.ptit.parking.payment.domain.ParkingPayment;
import com.ptit.parking.payment.domain.PointTransaction;
import com.ptit.parking.payment.domain.TransactionType;
import com.ptit.parking.payment.domain.Wallet;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * SV03, CT01 — lịch sử của ví. Dữ liệu: quản lý đổi 10.000 cho SV (id 1), SV trả phí 3.000 (id 2) và 5.000 (id 3)
 * cho chủ tài khoản, chủ tài khoản rút 2.000 (id 4).
 */
class GetMyTransactionsUseCaseTest {

    private static final long STUDENT = 3L;
    private static final long OWNER = 2L;
    private static final Instant NOW = Instant.parse("2026-10-27T01:00:00Z");

    private final InMemoryWalletRepository wallets = new InMemoryWalletRepository();
    private final InMemoryPointTransactionRepository transactions = new InMemoryPointTransactionRepository();
    private final GetMyTransactionsUseCase history = new GetMyTransactionsUseCase(wallets, transactions);

    private long studentWallet;
    private long ownerWallet;

    @BeforeEach
    void ghiSoCai() {
        ownerWallet = wallets.insert(new Wallet(null, OWNER, 0));
        studentWallet = wallets.insert(new Wallet(null, STUDENT, 0));
        Wallet student = wallets.lockById(studentWallet);
        Wallet owner = wallets.lockById(ownerWallet);
        ExchangeRate rate = new ExchangeRate(1);

        transactions.save(CashDeposit.execute(student, 10_000, rate, 1L, UUID.randomUUID(), NOW), "h1");
        transactions.save(ParkingPayment.execute(100L, student, owner, 3_000, 4L, UUID.randomUUID(), NOW), "h2");
        transactions.save(ParkingPayment.execute(101L, student, owner, 5_000, 4L, UUID.randomUUID(), NOW), "h3");
        transactions.save(CashWithdrawal.execute(owner, 2_000, rate, 1L, UUID.randomUUID(), NOW), "h4");
    }

    @Test
    void sinhVien_chiThayGiaoDichCuaViMinh_moiNhatTruoc_dauDungChieu() {
        Page page = history.history(STUDENT, null, null, 20);

        assertThat(page.walletId()).isEqualTo(studentWallet);
        assertThat(page.items()).extracting(PointTransaction::id).containsExactly(3L, 2L, 1L);   // không có lần rút (id 4)
        assertThat(page.items()).extracting(t -> t.changeFor(studentWallet)).containsExactly(-5_000L, -3_000L, 10_000L);
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextBefore()).isNull();
    }

    @Test
    void cungMotGiaoDichThanhToan_chuTaiKhoanThayCong_sinhVienThayTru() {
        PointTransaction payment = history.history(OWNER, null, null, 20).items().get(1);   // id 3

        assertThat(payment.id()).isEqualTo(3L);
        assertThat(payment.changeFor(ownerWallet)).isEqualTo(5_000);
        assertThat(payment.changeFor(studentWallet)).isEqualTo(-5_000);
        assertThat(payment.balanceBeforeFor(ownerWallet)).isEqualTo(3_000);
        assertThat(payment.balanceAfterFor(ownerWallet)).isEqualTo(8_000);
    }

    @Test
    void phanTrang_layThuaMotDong_deBietConDuLieu() {
        Page first = history.history(STUDENT, null, null, 2);
        assertThat(first.items()).extracting(PointTransaction::id).containsExactly(3L, 2L);
        assertThat(first.hasMore()).isTrue();
        assertThat(first.nextBefore()).isEqualTo(2L);

        Page second = history.history(STUDENT, null, first.nextBefore(), 2);
        assertThat(second.items()).extracting(PointTransaction::id).containsExactly(1L);
        assertThat(second.hasMore()).isFalse();
        assertThat(second.nextBefore()).isNull();
    }

    @Test
    void locTheoLoai() {
        assertThat(history.history(OWNER, TransactionType.RUT_DIEM, null, 20).items())
                .extracting(PointTransaction::id).containsExactly(4L);
        assertThat(history.history(STUDENT, TransactionType.DOI_DIEM, null, 20).items())
                .extracting(PointTransaction::id).containsExactly(1L);
    }
}