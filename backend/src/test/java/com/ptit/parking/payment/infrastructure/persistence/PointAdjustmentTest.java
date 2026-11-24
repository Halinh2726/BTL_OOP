package com.ptit.parking.payment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.payment.domain.PointAdjustment;
import com.ptit.parking.payment.domain.PointTransaction;
import com.ptit.parking.payment.domain.TransactionType;
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Đợt 3 — giao dịch điều chỉnh: lớp con thứ 4, chuyển điểm (bảo toàn tổng điểm), bắt buộc có lý do, ghi/đọc đúng cột. */
class PointAdjustmentTest {

    private static final Instant NOW = Instant.parse("2026-11-20T03:00:00Z");

    @Test
    void hoanDiem_chuyenTuChuBaiSangSinhVien_tongDiemKhongDoi() {
        Wallet owner = new Wallet(1L, 2L, 10_000);
        Wallet student = new Wallet(7L, 9L, 500);

        PointAdjustment adjustment = PointAdjustment.execute(owner, student, 3_000, "Trừ phí hai lần lượt #41",
                41L, 1L, UUID.randomUUID(), NOW);

        assertThat(adjustment.type()).isEqualTo(TransactionType.DIEU_CHINH);
        assertThat(owner.balance() + student.balance()).isEqualTo(10_500);              // R12
        assertThat(adjustment.changeFor(7L)).isEqualTo(3_000);
        assertThat(adjustment.balanceBeforeFor(7L)).isEqualTo(500);
        assertThat(adjustment.changeFor(1L)).isEqualTo(-3_000);
    }

    @Test
    void khongLyDo_hoacKhongDuDiem_biTuChoi_khongDoiSoDu() {
        Wallet owner = new Wallet(1L, 2L, 1_000);
        Wallet student = new Wallet(7L, 9L, 0);

        assertThatThrownBy(() -> PointAdjustment.execute(owner, student, 500, "  ", null, 1L, UUID.randomUUID(), NOW))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> PointAdjustment.execute(owner, student, 5_000, "Bồi thường", null, 1L,
                UUID.randomUUID(), NOW))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_POINTS));
        assertThat(student.balance()).isZero();
        assertThat(owner.balance()).isEqualTo(1_000);
    }

    @Test
    void ghiXuongDocLen_raDungLopConVaLyDo() {
        PointAdjustment original = PointAdjustment.execute(new Wallet(1L, 2L, 10_000), new Wallet(7L, 9L, 0), 3_000,
                "Bồi thường xe trầy theo quyết định chủ bãi", 41L, 1L, UUID.randomUUID(), NOW);
        PointTransactionRow row = PointTransactionRow.from(original);
        PointTransactionRow saved = new PointTransactionRow(99L, row.type(), row.points(), row.sourceWalletId(),
                row.targetWalletId(), row.sourceBalanceAfter(), row.targetBalanceAfter(), row.idempotencyKey(),
                row.performedBy(), row.createdAt(), row.cashAmount(), row.cashPerPoint(), row.sessionId(),
                row.originalTransactionId(), row.reason());

        PointTransaction restored = saved.toDomain();

        assertThat(restored).isExactlyInstanceOf(PointAdjustment.class);
        assertThat(((PointAdjustment) restored).reason()).isEqualTo("Bồi thường xe trầy theo quyết định chủ bãi");
        assertThat(((PointAdjustment) restored).originalTransactionId()).isEqualTo(41L);
        assertThat(restored.balanceAfterFor(7L)).isEqualTo(3_000);
    }
}
