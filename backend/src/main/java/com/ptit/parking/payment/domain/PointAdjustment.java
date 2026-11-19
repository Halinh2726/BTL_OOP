package com.ptit.parking.payment.domain;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * GiaoDichDieuChinh (Đợt 3) — lớp con THỨ TƯ của {@link PointTransaction}: CHUYỂN điểm giữa hai ví để sửa sai sót
 * mà KHÔNG sửa giao dịch cũ (R10). Ví dụ: sinh viên bị trừ phí hai lần → chuyển lại từ ví chủ bãi xe sang ví sinh viên;
 * chủ bãi xe quyết định bồi thường → chuyển điểm cho sinh viên.
 * <p>Vì là phép chuyển (có nguồn và đích, giống {@link ParkingPayment}) nên tổng điểm toàn hệ thống không đổi — R12 vẫn đúng.
 * Bắt buộc có lý do; có thể trỏ tới giao dịch gốc bị sai để truy vết.
 */
public final class PointAdjustment extends PointTransaction {

    public static final int MAX_REASON = 500;

    private final long sourceWalletId;
    private final long targetWalletId;
    private final long sourceBalanceAfter;
    private final long targetBalanceAfter;
    private final Long originalTransactionId;
    private final String reason;

    private PointAdjustment(Long id, long points, UUID idempotencyKey, long performedBy, Instant createdAt,
                            long sourceWalletId, long targetWalletId, long sourceBalanceAfter, long targetBalanceAfter,
                            Long originalTransactionId, String reason) {
        super(id, points, idempotencyKey, performedBy, createdAt);
        this.sourceWalletId = sourceWalletId;
        this.targetWalletId = targetWalletId;
        this.sourceBalanceAfter = sourceBalanceAfter;
        this.targetBalanceAfter = targetBalanceAfter;
        this.originalTransactionId = originalTransactionId;
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /**
     * Factory method: trừ ví nguồn TRƯỚC (không đủ điểm → INSUFFICIENT_POINTS, chưa cộng gì cho ví đích), rồi cộng ví đích.
     *
     * @param originalTransactionId giao dịch gốc bị sai (có thể null)
     */
    public static PointAdjustment execute(Wallet source, Wallet target, long points, String reason,
                                          Long originalTransactionId, long managerId, UUID idempotencyKey, Instant now) {
        String text = requireReason(reason);
        long sourceId = requirePersisted(source);
        long targetId = requirePersisted(target);
        if (sourceId == targetId) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR);
        }
        source.debit(points);
        target.credit(points);
        return new PointAdjustment(null, points, idempotencyKey, managerId, now, sourceId, targetId,
                source.balance(), target.balance(), originalTransactionId, text);
    }

    /** Chỉ dùng khi đọc giao dịch ĐÃ xảy ra từ CSDL lên. */
    public static PointAdjustment restore(long id, long points, UUID idempotencyKey, long performedBy, Instant createdAt,
                                          long sourceWalletId, long targetWalletId, long sourceBalanceAfter,
                                          long targetBalanceAfter, Long originalTransactionId, String reason) {
        return new PointAdjustment(id, points, idempotencyKey, performedBy, createdAt, sourceWalletId, targetWalletId,
                sourceBalanceAfter, targetBalanceAfter, originalTransactionId, reason);
    }

    @Override
    public TransactionType type() { return TransactionType.DIEU_CHINH; }

    @Override
    public long changeFor(long walletId) {
        if (walletId == sourceWalletId) return -points();
        if (walletId == targetWalletId) return points();
        return 0;
    }

    @Override
    public long balanceAfterFor(long walletId) {
        if (walletId == sourceWalletId) return sourceBalanceAfter;
        if (walletId == targetWalletId) return targetBalanceAfter;
        throw notInvolved(walletId);
    }

    public long sourceWalletId() { return sourceWalletId; }

    public long targetWalletId() { return targetWalletId; }

    public long sourceBalanceAfter() { return sourceBalanceAfter; }

    public long targetBalanceAfter() { return targetBalanceAfter; }

    public Long originalTransactionId() { return originalTransactionId; }

    public String reason() { return reason; }

    private static String requireReason(String reason) {
        String text = reason == null ? "" : reason.strip();
        if (text.isEmpty() || text.length() > MAX_REASON) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR);
        }
        return text;
    }
}
