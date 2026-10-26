package com.ptit.parking.payment.domain;

import java.time.Instant;
import java.util.UUID;

/** GiaoDichRutDiem (QL02) — TRỪ điểm ví chủ tài khoản, quản lý trả tiền mặt. Chỉ có ví nguồn. */
public final class CashWithdrawal extends PointTransaction {

    private final long sourceWalletId;
    private final long sourceBalanceAfter;
    private final long cashAmount;
    private final ExchangeRate rate;       // R11

    private CashWithdrawal(Long id, long points, UUID idempotencyKey, long performedBy, Instant createdAt,
                           long sourceWalletId, long sourceBalanceAfter, long cashAmount, ExchangeRate rate) {
        super(id, points, idempotencyKey, performedBy, createdAt);
        this.sourceWalletId = sourceWalletId;
        this.sourceBalanceAfter = sourceBalanceAfter;
        this.cashAmount = cashAmount;
        this.rate = rate;
    }

    /**
     * Factory method. R09: tham số là ví chủ tài khoản do hệ thống tự xác định
     * (system_config), không bao giờ nhận từ request.
     */
    public static CashWithdrawal execute(Wallet ownerWallet, long points, ExchangeRate rate,
                                         long managerId, UUID idempotencyKey, Instant now) {
        long cashAmount = rate.cashFor(points);
        ownerWallet.debit(points);                          // không đủ điểm -> INSUFFICIENT_POINTS
        return new CashWithdrawal(null, points, idempotencyKey, managerId, now,
                requirePersisted(ownerWallet), ownerWallet.balance(), cashAmount, rate);
    }

    /** Chỉ dùng khi đọc giao dịch ĐÃ xảy ra từ CSDL lên. */
    public static CashWithdrawal restore(long id, long points, UUID idempotencyKey, long performedBy, Instant createdAt,
                                         long sourceWalletId, long sourceBalanceAfter, long cashAmount, ExchangeRate rate) {
        return new CashWithdrawal(id, points, idempotencyKey, performedBy, createdAt,
                sourceWalletId, sourceBalanceAfter, cashAmount, rate);
    }

    @Override
    public TransactionType type() { return TransactionType.RUT_DIEM; }

    @Override
    public long changeFor(long walletId) {
        return walletId == sourceWalletId ? -points() : 0;
    }

    @Override
    public long balanceAfterFor(long walletId) {
        if (walletId != sourceWalletId) {
            throw notInvolved(walletId);
        }
        return sourceBalanceAfter;
    }

    public long sourceWalletId() { return sourceWalletId; }

    public long sourceBalanceAfter() { return sourceBalanceAfter; }

    public long cashAmount() { return cashAmount; }

    public ExchangeRate rate() { return rate; }
}
