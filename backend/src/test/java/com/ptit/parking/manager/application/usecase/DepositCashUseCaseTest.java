package com.ptit.parking.manager.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.manager.application.port.out.StudentDirectory;
import com.ptit.parking.manager.application.port.out.StudentDirectory.StudentAccount;
import com.ptit.parking.manager.application.usecase.DepositCashUseCase.Command;
import com.ptit.parking.manager.application.usecase.DepositCashUseCase.Result;
import com.ptit.parking.manager.application.usecase.LookupStudentUseCase.StudentSummary;
import com.ptit.parking.payment.application.IdempotencyGuard;
import com.ptit.parking.payment.application.port.out.InMemoryPointTransactionRepository;
import com.ptit.parking.payment.application.port.out.InMemoryWalletRepository;
import com.ptit.parking.payment.domain.CashDeposit;
import com.ptit.parking.payment.domain.ExchangeRate;
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** QL01 — đổi tiền mặt sang điểm, và tra cứu sinh viên trước khi đổi (R08). Không cần CSDL, không cần Spring. */
class DepositCashUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-27T01:00:00Z");
    private static final long MANAGER = 1L;
    private static final long STUDENT_USER = 3L;

    private final InMemoryWalletRepository wallets = new InMemoryWalletRepository();
    private final long studentWallet = wallets.insert(new Wallet(null, STUDENT_USER, 0));
    private final StudentDirectory students = code -> "B21DCCN001".equals(code)
            ? Optional.of(new StudentAccount(STUDENT_USER, "B21DCCN001", "Nguyễn Văn A", studentWallet))
            : Optional.empty();
    private final InMemoryPointTransactionRepository transactions = new InMemoryPointTransactionRepository();
    private final TransactionRunner directly = new TransactionRunner() {
        @Override
        public <T> T inTransaction(Supplier<T> work) {
            return work.get();
        }
    };

    private ExchangeRate rate = new ExchangeRate(1);

    private final DepositCashUseCase depositCash = new DepositCashUseCase(students, wallets, () -> rate,
            new IdempotencyGuard(transactions, directly), Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh")));
    private final LookupStudentUseCase lookupStudent = new LookupStudentUseCase(students, wallets);

    @Test
    void doiDiemThanhCong_congDiem_luuGiaoDichKemTyLe_nguoiThucHienLaQuanLy() {
        Result result = depositCash.deposit(new Command(MANAGER, UUID.randomUUID(), " b21dccn001 ", 50_000));

        CashDeposit deposit = result.deposit();
        assertThat(result.studentCode()).isEqualTo("B21DCCN001");
        assertThat(result.replayed()).isFalse();
        assertThat(deposit.points()).isEqualTo(50_000);
        assertThat(deposit.balanceBeforeFor(studentWallet)).isZero();
        assertThat(deposit.balanceAfterFor(studentWallet)).isEqualTo(50_000);
        assertThat(deposit.rate()).isEqualTo(new ExchangeRate(1));          // R11
        assertThat(deposit.performedBy()).isEqualTo(MANAGER);
        assertThat(deposit.createdAt()).isEqualTo(NOW);
        assertThat(wallets.balanceOf(studentWallet)).isEqualTo(50_000);      // số dư ĐÃ LƯU
        assertThat(wallets.lockOrder()).containsExactly(studentWallet);      // có khóa ví trước khi cộng
    }

    @Test
    void quanLyBamXacNhanHaiLan_chiCongDiemMotLan() {
        UUID key = UUID.randomUUID();
        depositCash.deposit(new Command(MANAGER, key, "B21DCCN001", 50_000));

        Result again = depositCash.deposit(new Command(MANAGER, key, "B21DCCN001", 50_000));

        assertThat(again.replayed()).isTrue();
        assertThat(wallets.balanceOf(studentWallet)).isEqualTo(50_000);      // KHÔNG phải 100.000
        assertThat(transactions.all()).hasSize(1);
    }

    @Test
    void cungKeyNhungKhacSoTien_baoIdempotencyKeyReused() {
        UUID key = UUID.randomUUID();
        depositCash.deposit(new Command(MANAGER, key, "B21DCCN001", 50_000));

        assertError(() -> depositCash.deposit(new Command(MANAGER, key, "B21DCCN001", 70_000)),
                ErrorCode.IDEMPOTENCY_KEY_REUSED);
        assertThat(wallets.balanceOf(studentWallet)).isEqualTo(50_000);
    }

    @Test
    void R02_soTienKhongHopLe_baoInvalidAmount_khongCongGi() {
        rate = new ExchangeRate(1000);                                       // 1 điểm = 1.000 đ
        assertError(() -> depositCash.deposit(new Command(MANAGER, UUID.randomUUID(), "B21DCCN001", 1_500)),
                ErrorCode.INVALID_AMOUNT);                                    // không chia hết
        assertError(() -> depositCash.deposit(new Command(MANAGER, UUID.randomUUID(), "B21DCCN001", 0)),
                ErrorCode.INVALID_AMOUNT);
        assertThat(wallets.balanceOf(studentWallet)).isZero();
        assertThat(transactions.all()).isEmpty();
    }

    @Test
    void mssvKhongTonTai_baoStudentNotFound_nhungSoTienSaiThiBaoSoTienTruoc() {
        assertError(() -> depositCash.deposit(new Command(MANAGER, UUID.randomUUID(), "B99XXXX", 10_000)),
                ErrorCode.STUDENT_NOT_FOUND);
        assertError(() -> depositCash.deposit(new Command(MANAGER, UUID.randomUUID(), "B99XXXX", -5)),
                ErrorCode.INVALID_AMOUNT);                                    // Đặc tả: bước 2 (R02) trước bước 3
    }

    @Test
    void traCuuSinhVien_R08_hoTenVaSoDu_mssvChuThuongVanTimDuoc() {
        depositCash.deposit(new Command(MANAGER, UUID.randomUUID(), "B21DCCN001", 20_000));

        assertThat(lookupStudent.lookup("b21dccn001"))
                .isEqualTo(new StudentSummary("B21DCCN001", "Nguyễn Văn A", 20_000));
        assertError(() -> lookupStudent.lookup("B99XXXX"), ErrorCode.STUDENT_NOT_FOUND);
    }

    private static void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(expected));
    }
}