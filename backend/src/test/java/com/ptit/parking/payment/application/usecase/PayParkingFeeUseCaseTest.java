package com.ptit.parking.payment.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.payment.application.port.out.InMemoryWalletRepository;
import com.ptit.parking.payment.application.usecase.PayParkingFeeUseCase.Command;
import com.ptit.parking.payment.domain.ParkingPayment;
import com.ptit.parking.payment.domain.TransactionType;
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** HT01 — trừ ví sinh viên, cộng ví nhận phí lấy từ cấu hình (R06); khóa 2 ví theo id tăng dần. */
class PayParkingFeeUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-05T11:30:00Z");
    private static final UUID KEY = UUID.randomUUID();
    private static final long STUDENT = 3L;
    private static final long OWNER = 2L;
    private static final long SESSION = 501L;
    private static final long GUARD = 4L;

    private final InMemoryWalletRepository wallets = new InMemoryWalletRepository();
    // Ví nhận được tạo TRƯỚC nên có id NHỎ hơn ví sinh viên — để kiểm tra thứ tự khóa theo id, không theo vai trò
    private final long receiverWallet = wallets.insert(new Wallet(null, OWNER, 100));
    private final long studentWallet = wallets.insert(new Wallet(null, STUDENT, 10_000));

    private final PayParkingFeeUseCase payParkingFee = new PayParkingFeeUseCase(wallets, () -> receiverWallet);

    @Test
    void truViSinhVien_congViNhan_giaoDichGhiDuSoDuHaiBen() {
        ParkingPayment payment = payParkingFee.pay(new Command(SESSION, STUDENT, 5_000, GUARD, KEY, NOW));

        assertThat(payment.type()).isEqualTo(TransactionType.THANH_TOAN);
        assertThat(payment.id()).isNull();                                       // bên gọi mới lưu giao dịch
        assertThat(payment.sessionId()).isEqualTo(SESSION);
        assertThat(payment.points()).isEqualTo(5_000);
        assertThat(payment.sourceWalletId()).isEqualTo(studentWallet);
        assertThat(payment.targetWalletId()).isEqualTo(receiverWallet);           // R06: ví nhận từ cấu hình
        assertThat(payment.sourceBalanceAfter()).isEqualTo(5_000);
        assertThat(payment.targetBalanceAfter()).isEqualTo(5_100);
        assertThat(payment.performedBy()).isEqualTo(GUARD);
        assertThat(payment.createdAt()).isEqualTo(NOW);
        assertThat(wallets.balanceOf(studentWallet)).isEqualTo(5_000);            // số dư ĐÃ LƯU
        assertThat(wallets.balanceOf(receiverWallet)).isEqualTo(5_100);
    }

    @Test
    void khoaHaiViTheoIdTangDan_khongTheoVaiTro() {
        payParkingFee.pay(new Command(SESSION, STUDENT, 3_000, GUARD, KEY, NOW));

        assertThat(wallets.lockOrder()).containsExactly(receiverWallet, studentWallet);
        assertThat(receiverWallet).isLessThan(studentWallet);
    }

    @Test
    void thieuDiem_baoInsufficientPoints_haiViKhongDoi() {
        assertThatThrownBy(() -> payParkingFee.pay(new Command(SESSION, STUDENT, 10_001, GUARD, KEY, NOW)))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_POINTS));

        assertThat(wallets.balanceOf(studentWallet)).isEqualTo(10_000);
        assertThat(wallets.balanceOf(receiverWallet)).isEqualTo(100);             // không cộng gì cho chủ tài khoản
    }

    @Test
    void dungBangSoDu_vanTraDuoc_conLai0() {
        payParkingFee.pay(new Command(SESSION, STUDENT, 10_000, GUARD, KEY, NOW));

        assertThat(wallets.balanceOf(studentWallet)).isZero();                    // R01: được về 0, không âm
        assertThat(wallets.balanceOf(receiverWallet)).isEqualTo(10_100);
    }
}
