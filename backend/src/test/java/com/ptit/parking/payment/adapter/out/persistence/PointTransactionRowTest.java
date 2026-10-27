package com.ptit.parking.payment.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ptit.parking.payment.domain.CashDeposit;
import com.ptit.parking.payment.domain.CashWithdrawal;
import com.ptit.parking.payment.domain.ExchangeRate;
import com.ptit.parking.payment.domain.ParkingPayment;
import com.ptit.parking.payment.domain.PointTransaction;
import com.ptit.parking.payment.domain.TransactionType;
import com.ptit.parking.payment.domain.Wallet;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Chuyển đổi giao dịch ↔ cột CSDL cho cả 3 loại: đúng cột nào có giá trị, cột nào NULL (khớp ck_point_transactions_shape),
 * và đọc lên dựng lại ĐÚNG lớp con với đầy đủ dữ liệu.
 */
class PointTransactionRowTest {

    private static final Instant NOW = Instant.parse("2026-10-27T01:00:00Z");
    private static final ExchangeRate ONE_TO_ONE = new ExchangeRate(1);

    @Test
    void doiDiem_chiCoViDich_vaChiTietTienMat() {
        Wallet student = new Wallet(10L, 3L, 0);
        CashDeposit deposit = CashDeposit.execute(student, 50_000, ONE_TO_ONE, 1L, UUID.randomUUID(), NOW);

        PointTransactionRow row = PointTransactionRow.from(deposit);

        assertThat(row.type()).isEqualTo(TransactionType.DOI_DIEM);
        assertThat(row.sourceWalletId()).isNull();
        assertThat(row.sourceBalanceAfter()).isNull();
        assertThat(row.targetWalletId()).isEqualTo(10L);
        assertThat(row.targetBalanceAfter()).isEqualTo(50_000L);
        assertThat(row.cashAmount()).isEqualTo(50_000L);
        assertThat(row.cashPerPoint()).isEqualTo(1);
        assertThat(row.sessionId()).isNull();

        assertRoundTrip(deposit, row, 10L);
    }

    @Test
    void rutDiem_chiCoViNguon_vaChiTietTienMat() {
        Wallet owner = new Wallet(1L, 2L, 20_000);
        CashWithdrawal withdrawal = CashWithdrawal.execute(owner, 5_000, ONE_TO_ONE, 1L, UUID.randomUUID(), NOW);

        PointTransactionRow row = PointTransactionRow.from(withdrawal);

        assertThat(row.type()).isEqualTo(TransactionType.RUT_DIEM);
        assertThat(row.sourceWalletId()).isEqualTo(1L);
        assertThat(row.sourceBalanceAfter()).isEqualTo(15_000L);
        assertThat(row.targetWalletId()).isNull();
        assertThat(row.targetBalanceAfter()).isNull();
        assertThat(row.cashAmount()).isEqualTo(5_000L);
        assertThat(row.sessionId()).isNull();

        assertRoundTrip(withdrawal, row, 1L);
    }

    @Test
    void thanhToan_coCaHaiVi_vaLuotGui_khongCoTienMat() {
        Wallet student = new Wallet(10L, 3L, 8_000);
        Wallet owner = new Wallet(1L, 2L, 0);
        ParkingPayment payment = ParkingPayment.execute(77L, student, owner, 5_000, 4L, UUID.randomUUID(), NOW);

        PointTransactionRow row = PointTransactionRow.from(payment);

        assertThat(row.type()).isEqualTo(TransactionType.THANH_TOAN);
        assertThat(row.sourceWalletId()).isEqualTo(10L);
        assertThat(row.targetWalletId()).isEqualTo(1L);
        assertThat(row.sourceBalanceAfter()).isEqualTo(3_000L);
        assertThat(row.targetBalanceAfter()).isEqualTo(5_000L);
        assertThat(row.cashAmount()).isNull();
        assertThat(row.sessionId()).isEqualTo(77L);

        assertRoundTrip(payment, row, 10L);
        assertRoundTrip(payment, row, 1L);
    }

    /** Ghi xuống rồi đọc lên (giả lập CSDL gán id = 99): ra đúng lớp con, đúng điểm trước/sau với ví đó. */
    private static void assertRoundTrip(PointTransaction original, PointTransactionRow row, long walletId) {
        PointTransactionRow saved = new PointTransactionRow(99L, row.type(), row.points(),
                row.sourceWalletId(), row.targetWalletId(), row.sourceBalanceAfter(), row.targetBalanceAfter(),
                row.idempotencyKey(), row.performedBy(), row.createdAt(),
                row.cashAmount(), row.cashPerPoint(), row.sessionId());

        PointTransaction restored = saved.toDomain();

        assertThat(restored).isExactlyInstanceOf(original.getClass());
        assertThat(restored.id()).isEqualTo(99L);
        assertThat(restored.points()).isEqualTo(original.points());
        assertThat(restored.idempotencyKey()).isEqualTo(original.idempotencyKey());
        assertThat(restored.performedBy()).isEqualTo(original.performedBy());
        assertThat(restored.createdAt()).isEqualTo(original.createdAt());
        assertThat(restored.changeFor(walletId)).isEqualTo(original.changeFor(walletId));
        assertThat(restored.balanceBeforeFor(walletId)).isEqualTo(original.balanceBeforeFor(walletId));
        assertThat(restored.balanceAfterFor(walletId)).isEqualTo(original.balanceAfterFor(walletId));
    }
}
