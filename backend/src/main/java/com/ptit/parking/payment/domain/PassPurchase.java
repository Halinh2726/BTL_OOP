package com.ptit.parking.payment.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Đợt 5 — GiaoDichMuaVe: sinh viên trả điểm cho chủ bãi xe để mua vé tháng. Lớp con THỨ 5 của
 * {@link PointTransaction}: thêm lớp này, trình biên dịch tự báo mọi {@code switch} trên lớp sealed còn thiếu
 * trường hợp — không có chỗ nào bị "quên" xử lý loại giao dịch mới.
 * <p>Giống thanh toán phí: có ví nguồn (sinh viên) và ví đích (chủ bãi xe); là phép chuyển nên R12 vẫn đúng.
 */
public final class PassPurchase extends PointTransaction {

    private final long sourceWalletId;
    private final long targetWalletId;
    private final long sourceBalanceAfter;
    private final long targetBalanceAfter;

    private PassPurchase(Long id, long points, UUID idempotencyKey, long performedBy, Instant createdAt,
                         long sourceWalletId, long targetWalletId, long sourceBalanceAfter, long targetBalanceAfter) {
        super(id, points, idempotencyKey, performedBy, createdAt);
        this.sourceWalletId = sourceWalletId;
        this.targetWalletId = targetWalletId;
        this.sourceBalanceAfter = sourceBalanceAfter;
        this.targetBalanceAfter = targetBalanceAfter;
    }

    /** Trừ ví sinh viên TRƯỚC: không đủ điểm thì ném INSUFFICIENT_POINTS khi chưa cộng gì cho chủ bãi. */
    public static PassPurchase execute(Wallet studentWallet, Wallet ownerWallet, long price, long studentId,
                                       UUID idempotencyKey, Instant now) {
        long sourceId = requirePersisted(studentWallet);
        long targetId = requirePersisted(ownerWallet);
        if (sourceId == targetId) {
            throw new IllegalArgumentException("Ví nguồn và ví đích phải khác nhau");
        }
        studentWallet.debit(price);
        ownerWallet.credit(price);
        return new PassPurchase(null, price, idempotencyKey, studentId, now, sourceId, targetId,
                studentWallet.balance(), ownerWallet.balance());
    }

    /** Chỉ dùng khi đọc giao dịch ĐÃ xảy ra từ CSDL lên. */
    public static PassPurchase restore(long id, long points, UUID idempotencyKey, long performedBy, Instant createdAt,
                                       long sourceWalletId, long targetWalletId, long sourceBalanceAfter,
                                       long targetBalanceAfter) {
        return new PassPurchase(id, points, idempotencyKey, performedBy, createdAt, sourceWalletId, targetWalletId,
                sourceBalanceAfter, targetBalanceAfter);
    }

    @Override
    public TransactionType type() { return TransactionType.MUA_VE; }

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
}
