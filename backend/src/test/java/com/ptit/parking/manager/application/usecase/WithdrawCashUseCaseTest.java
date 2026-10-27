package com.ptit.parking.manager.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.manager.application.port.out.OwnerAccountDirectory;
import com.ptit.parking.manager.application.port.out.OwnerAccountDirectory.OwnerAccount;
import com.ptit.parking.manager.application.usecase.GetOwnerWalletUseCase.OwnerWallet;
import com.ptit.parking.manager.application.usecase.WithdrawCashUseCase.Command;
import com.ptit.parking.manager.application.usecase.WithdrawCashUseCase.Result;
import com.ptit.parking.payment.application.IdempotencyGuard;
import com.ptit.parking.payment.application.port.out.InMemoryPointTransactionRepository;
import com.ptit.parking.payment.application.port.out.InMemoryWalletRepository;
import com.ptit.parking.payment.domain.CashWithdrawal;
import com.ptit.parking.payment.domain.ExchangeRate;
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.function.Supplier;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/** QL02 — rút điểm của chủ tài khoản: ví nguồn do hệ thống xác định (R09), trừ đúng một lần, không âm. */
class WithdrawCashUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-06T02:00:00Z");
    private static final long MANAGER = 1L;
    private static final long OWNER_USER = 2L;
    private static final long STUDENT_USER = 3L;

    private final InMemoryWalletRepository wallets = new InMemoryWalletRepository();
    private final long studentWallet = wallets.insert(new Wallet(null, STUDENT_USER, 9_999));
    private final long ownerWallet = wallets.insert(new Wallet(null, OWNER_USER, 5_000));
    private final OwnerAccountDirectory owners = () -> new OwnerAccount(OWNER_USER, "Lê Văn C", ownerWallet);
    private final InMemoryPointTransactionRepository transactions = new InMemoryPointTransactionRepository();
    private final TransactionRunner directly = new TransactionRunner() {
        @Override
        public <T> T inTransaction(Supplier<T> work) {
            return work.get();
        }
    };

    private ExchangeRate rate = new ExchangeRate(1);

    private final WithdrawCashUseCase withdrawCash = new WithdrawCashUseCase(owners, wallets, () -> rate,
            new IdempotencyGuard(transactions, directly), Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh")));
    private final GetOwnerWalletUseCase getOwnerWallet = new GetOwnerWalletUseCase(owners, wallets);

    @Test
    void xemViChuTaiKhoan_hoTenVaSoDiem() {
        assertThat(getOwnerWallet.ownerWallet()).isEqualTo(new OwnerWallet("Lê Văn C", 5_000));
    }

    @Test
    void rutThanhCong_truViChuTaiKhoan_luuGiaoDichKemTyLe_nguoiThucHienLaQuanLy() {
        Result result = withdrawCash.withdraw(new Command(MANAGER, UUID.randomUUID(), 2_000));

        CashWithdrawal withdrawal = result.withdrawal();
        assertThat(result.replayed()).isFalse();
        assertThat(withdrawal.id()).isNotNull();
        assertThat(withdrawal.sourceWalletId()).isEqualTo(ownerWallet);          // R09: ví do hệ thống xác định
        assertThat(withdrawal.points()).isEqualTo(2_000);
        assertThat(withdrawal.cashAmount()).isEqualTo(2_000);
        assertThat(withdrawal.rate()).isEqualTo(new ExchangeRate(1));            // R11
        assertThat(withdrawal.balanceBeforeFor(ownerWallet)).isEqualTo(5_000);
        assertThat(withdrawal.balanceAfterFor(ownerWallet)).isEqualTo(3_000);
        assertThat(withdrawal.performedBy()).isEqualTo(MANAGER);
        assertThat(withdrawal.createdAt()).isEqualTo(NOW);
        assertThat(wallets.balanceOf(ownerWallet)).isEqualTo(3_000);              // số dư ĐÃ LƯU
        assertThat(wallets.balanceOf(studentWallet)).isEqualTo(9_999);            // ví khác không bị đụng tới
        assertThat(wallets.lockOrder()).containsExactly(ownerWallet);             // có khóa ví trước khi trừ
    }

    @Test
    void tyLe2DongMotDiem_soTienMatGapDoi() {
        rate = new ExchangeRate(2);

        Result result = withdrawCash.withdraw(new Command(MANAGER, UUID.randomUUID(), 2_000));

        assertThat(result.withdrawal().cashAmount()).isEqualTo(4_000);
        assertThat(wallets.balanceOf(ownerWallet)).isEqualTo(3_000);
    }

    @Test
    void rutHetSoDu_conLai0() {
        withdrawCash.withdraw(new Command(MANAGER, UUID.randomUUID(), 5_000));

        assertThat(wallets.balanceOf(ownerWallet)).isZero();
    }

    @Test
    void rutVuotSoDu_INSUFFICIENT_POINTS_viKhongDoi_khongCoGiaoDich() {
        assertError(() -> withdrawCash.withdraw(new Command(MANAGER, UUID.randomUUID(), 5_001)),
                ErrorCode.INSUFFICIENT_POINTS);

        assertThat(wallets.balanceOf(ownerWallet)).isEqualTo(5_000);
        assertThat(transactions.all()).isEmpty();
    }

    @Test
    void soDiemKhongDuong_INVALID_AMOUNT_khongKhoaVi() {
        assertError(() -> withdrawCash.withdraw(new Command(MANAGER, UUID.randomUUID(), 0)), ErrorCode.INVALID_AMOUNT);
        assertError(() -> withdrawCash.withdraw(new Command(MANAGER, UUID.randomUUID(), -100)), ErrorCode.INVALID_AMOUNT);

        assertThat(wallets.lockOrder()).isEmpty();
        assertThat(transactions.all()).isEmpty();
    }

    @Test
    void bamXacNhanHaiLanCungKey_chiTruMotLan() {
        UUID key = UUID.randomUUID();
        Result first = withdrawCash.withdraw(new Command(MANAGER, key, 2_000));

        Result again = withdrawCash.withdraw(new Command(MANAGER, key, 2_000));

        assertThat(again.replayed()).isTrue();
        assertThat(again.withdrawal().id()).isEqualTo(first.withdrawal().id());
        assertThat(wallets.balanceOf(ownerWallet)).isEqualTo(3_000);              // KHÔNG phải 1.000
        assertThat(transactions.all()).hasSize(1);
    }

    @Test
    void cungKeyKhacSoDiem_IDEMPOTENCY_KEY_REUSED() {
        UUID key = UUID.randomUUID();
        withdrawCash.withdraw(new Command(MANAGER, key, 2_000));

        assertError(() -> withdrawCash.withdraw(new Command(MANAGER, key, 1_000)), ErrorCode.IDEMPOTENCY_KEY_REUSED);
        assertThat(wallets.balanceOf(ownerWallet)).isEqualTo(3_000);
    }

    private static void assertError(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(expected));
    }
}
