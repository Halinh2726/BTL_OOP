package com.ptit.parking.payment.adapter.out.persistence;

import com.ptit.parking.payment.domain.CashDeposit;
import com.ptit.parking.payment.domain.CashWithdrawal;
import com.ptit.parking.payment.domain.ExchangeRate;
import com.ptit.parking.payment.domain.ParkingPayment;
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
        Long sessionId) {                           // chỉ THANH_TOAN (parking_payment_details)

    /**
     * Domain → cột. {@code switch} trên lớp sealed: trình biên dịch biết CHỈ có 3 lớp con nên không cần
     * {@code default}; thêm loại giao dịch thứ 4 mà quên xử lý ở đây thì lỗi biên dịch ngay.
     */
    static PointTransactionRow from(PointTransaction tx) {
        return switch (tx) {
            case CashDeposit d -> new PointTransactionRow(d.id(), d.type(), d.points(),
                    null, d.targetWalletId(), null, d.targetBalanceAfter(),
                    d.idempotencyKey(), d.performedBy(), d.createdAt(),
                    d.cashAmount(), d.rate().cashPerPoint(), null);
            case CashWithdrawal w -> new PointTransactionRow(w.id(), w.type(), w.points(),
                    w.sourceWalletId(), null, w.sourceBalanceAfter(), null,
                    w.idempotencyKey(), w.performedBy(), w.createdAt(),
                    w.cashAmount(), w.rate().cashPerPoint(), null);
            case ParkingPayment p -> new PointTransactionRow(p.id(), p.type(), p.points(),
                    p.sourceWalletId(), p.targetWalletId(), p.sourceBalanceAfter(), p.targetBalanceAfter(),
                    p.idempotencyKey(), p.performedBy(), p.createdAt(),
                    null, null, p.sessionId());
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
        };
    }
}