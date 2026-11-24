package com.ptit.parking.payment.infrastructure.persistence;

import com.ptit.parking.payment.domain.CashDeposit;
import com.ptit.parking.payment.domain.CashWithdrawal;
import com.ptit.parking.payment.domain.ExchangeRate;
import com.ptit.parking.payment.domain.ParkingPayment;
import com.ptit.parking.payment.domain.PassPurchase;
import com.ptit.parking.payment.domain.PointAdjustment;
import com.ptit.parking.payment.domain.PointTransaction;
import com.ptit.parking.payment.domain.TransactionType;
import java.time.Instant;
import java.util.UUID;

/**
 * Một giao dịch dưới dạng "các cột" của point_transactions + bảng chi tiết. Tách khỏi adapter SQL để việc
 * chuyển đổi domain ↔ cột được kiểm tra bằng unit test, không cần CSDL.
 * <p>Cột không áp dụng cho loại giao dịch đó là {@code null} (ví dụ DOI_DIEM không có ví nguồn) — khớp
 * ràng buộc ck_point_transactions_shape của CSDL.
 */
record PointTransactionRow(
        Long id, TransactionType type, long points,
        Long sourceWalletId, Long targetWalletId, Long sourceBalanceAfter, Long targetBalanceAfter,
        UUID idempotencyKey, long performedBy, Instant createdAt,
        Long cashAmount, Integer cashPerPoint,      // chỉ DOI_DIEM, RUT_DIEM (cash_details)
        Long sessionId,                             // chỉ THANH_TOAN (parking_payment_details)
        Long originalTransactionId, String reason) { // chỉ DIEU_CHINH (adjustment_details)

    /**
     * Domain → cột. {@code switch} trên lớp sealed: trình biên dịch biết CHỈ có 4 lớp con nên không cần
     * {@code default}; khi Đợt 3 thêm PointAdjustment, chính trình biên dịch đã báo lỗi ở đây cho tới khi xử lý đủ.
     */
    static PointTransactionRow from(PointTransaction tx) {
        return switch (tx) {
            case CashDeposit d -> new PointTransactionRow(d.id(), d.type(), d.points(),
                    null, d.targetWalletId(), null, d.targetBalanceAfter(),
                    d.idempotencyKey(), d.performedBy(), d.createdAt(),
                    d.cashAmount(), d.rate().cashPerPoint(), null, null, null);
            case CashWithdrawal w -> new PointTransactionRow(w.id(), w.type(), w.points(),
                    w.sourceWalletId(), null, w.sourceBalanceAfter(), null,
                    w.idempotencyKey(), w.performedBy(), w.createdAt(),
                    w.cashAmount(), w.rate().cashPerPoint(), null, null, null);
            case ParkingPayment p -> new PointTransactionRow(p.id(), p.type(), p.points(),
                    p.sourceWalletId(), p.targetWalletId(), p.sourceBalanceAfter(), p.targetBalanceAfter(),
                    p.idempotencyKey(), p.performedBy(), p.createdAt(),
                    null, null, p.sessionId(), null, null);
            case PointAdjustment a -> new PointTransactionRow(a.id(), a.type(), a.points(),
                    a.sourceWalletId(), a.targetWalletId(), a.sourceBalanceAfter(), a.targetBalanceAfter(),
                    a.idempotencyKey(), a.performedBy(), a.createdAt(),
                    null, null, null, a.originalTransactionId(), a.reason());
            // Đợt 5: lớp con thứ 5 — trình biên dịch báo lỗi ở đây (và ở toDomain) cho tới khi thêm 2 nhánh này
            case PassPurchase m -> new PointTransactionRow(m.id(), m.type(), m.points(),
                    m.sourceWalletId(), m.targetWalletId(), m.sourceBalanceAfter(), m.targetBalanceAfter(),
                    m.idempotencyKey(), m.performedBy(), m.createdAt(),
                    null, null, null, null, null);
        };
    }

    /** Cột → domain: dựng lại ĐÚNG lớp con theo cột type, qua factory {@code restore} của từng lớp. */
    PointTransaction toDomain() {
        return switch (type) {
            case DOI_DIEM -> CashDeposit.restore(id, points, idempotencyKey, performedBy, createdAt,
                    targetWalletId, targetBalanceAfter, cashAmount, new ExchangeRate(cashPerPoint));
            case RUT_DIEM -> CashWithdrawal.restore(id, points, idempotencyKey, performedBy, createdAt,
                    sourceWalletId, sourceBalanceAfter, cashAmount, new ExchangeRate(cashPerPoint));
            case THANH_TOAN -> ParkingPayment.restore(id, points, idempotencyKey, performedBy, createdAt,
                    sessionId, sourceWalletId, targetWalletId, sourceBalanceAfter, targetBalanceAfter);
            case DIEU_CHINH -> PointAdjustment.restore(id, points, idempotencyKey, performedBy, createdAt,
                    sourceWalletId, targetWalletId, sourceBalanceAfter, targetBalanceAfter, originalTransactionId, reason);
            case MUA_VE -> PassPurchase.restore(id, points, idempotencyKey, performedBy, createdAt,
                    sourceWalletId, targetWalletId, sourceBalanceAfter, targetBalanceAfter);
        };
    }
}