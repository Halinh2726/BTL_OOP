package com.ptit.parking.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PointTransactionTest {

    private static final long MANAGER_ID = 1L;
    private static final long GUARD_ID = 4L;
    private static final ExchangeRate RATE_1_1 = new ExchangeRate(1);
    private static final Instant NOW = Instant.parse("2026-10-05T11:30:00Z");

    private final Wallet student = new Wallet(10L, 3L, 0);
    private final Wallet owner = new Wallet(20L, 2L, 0);

    @Test
    void daHinh_moiLoaiGiaoDichTuTinhDiemThayDoiCuaMinh() {
        // Kịch bản thật: đổi 10.000đ -> gửi xe 5.000 điểm -> chủ rút 2.000 điểm
        List<PointTransaction> history = List.of(
                CashDeposit.execute(student, 10_000, RATE_1_1, MANAGER_ID, UUID.randomUUID(), NOW),
                ParkingPayment.execute(501L, student, owner, 5_000, GUARD_ID, UUID.randomUUID(), NOW),
                CashWithdrawal.execute(owner, 2_000, RATE_1_1, MANAGER_ID, UUID.randomUUID(), NOW));

        // CÙNG một lời gọi changeFor(), KHÔNG có if/else theo loại — JVM tự chọn cài đặt của lớp con
        assertThat(history).extracting(t -> t.changeFor(student.id())).containsExactly(10_000L, -5_000L, 0L);
        assertThat(history).extracting(t -> t.changeFor(owner.id())).containsExactly(0L, 5_000L, -2_000L);
        assertThat(history).extracting(PointTransaction::type)
                .containsExactly(TransactionType.DOI_DIEM, TransactionType.THANH_TOAN, TransactionType.RUT_DIEM);
    }

    @Test
    void doiDiem_congDiemVaGhiDiemTruocSau() {
        CashDeposit deposit = CashDeposit.execute(student, 10_000, RATE_1_1, MANAGER_ID, UUID.randomUUID(), NOW);

        assertThat(student.balance()).isEqualTo(10_000);
        assertThat(deposit.points()).isEqualTo(10_000);
        assertThat(deposit.balanceBeforeFor(student.id())).isZero();
        assertThat(deposit.balanceAfterFor(student.id())).isEqualTo(10_000);
        assertThat(deposit.rate()).isEqualTo(RATE_1_1);
    }

    @Test
    void doiDiem_soTienKhongLaBoiSoTyLe_biTuChoi() {
        ExchangeRate rate = new ExchangeRate(1_000);    // 1.000đ = 1 điểm

        assertThatThrownBy(() -> CashDeposit.execute(student, 1_500, rate, MANAGER_ID, UUID.randomUUID(), NOW))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_AMOUNT));
        assertThat(student.balance()).isZero();         // R02: không tạo giao dịch, ví không đổi
    }

    @Test
    void thanhToan_khongDuDiem_haiViDeuKhongDoi() {
        Wallet poorStudent = new Wallet(11L, 5L, 2_000);

        assertThatThrownBy(() -> ParkingPayment.execute(501L, poorStudent, owner, 3_000, GUARD_ID, UUID.randomUUID(), NOW))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_POINTS));
        assertThat(poorStudent.balance()).isEqualTo(2_000);
        assertThat(owner.balance()).isZero();
    }

    @Test
    void thanhToan_baoToanDiem() {
        Wallet richStudent = new Wallet(12L, 6L, 10_000);

        ParkingPayment payment = ParkingPayment.execute(501L, richStudent, owner, 5_000, GUARD_ID, UUID.randomUUID(), NOW);

        assertThat(richStudent.balance() + owner.balance()).isEqualTo(10_000);   // R12: điểm không tự sinh ra/mất đi
        assertThat(payment.balanceBeforeFor(richStudent.id())).isEqualTo(10_000);
        assertThat(payment.balanceBeforeFor(owner.id())).isZero();
        assertThat(payment.performedBy()).isEqualTo(GUARD_ID);
    }

    @Test
    void rutDiem_vuotSoDu_biTuChoi() {
        assertThatThrownBy(() -> CashWithdrawal.execute(owner, 1, RATE_1_1, MANAGER_ID, UUID.randomUUID(), NOW))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_POINTS));
    }
}
