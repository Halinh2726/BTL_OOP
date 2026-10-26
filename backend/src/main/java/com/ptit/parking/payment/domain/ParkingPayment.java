package com.ptit.parking.payment.domain;

import java.time.Instant;
import java.util.UUID;

/** GiaoDichThanhToan (HT01) — chuyển phí gửi xe từ ví sinh viên sang ví chủ tài khoản. Có cả nguồn và đích. */
public final class ParkingPayment extends PointTransaction {

    private final long sessionId;          // lượt gửi được thanh toán (mỗi lượt chỉ 1 lần)
    private final long sourceWalletId;
    private final long targetWalletId;
    private final long sourceBalanceAfter;
    private final long targetBalanceAfter;

    private ParkingPayment(Long id, long points, UUID idempotencyKey, long performedBy, Instant createdAt,
                           long sessionId, long sourceWalletId, long targetWalletId,
                           long sourceBalanceAfter, long targetBalanceAfter) {
        super(id, points, idempotencyKey, performedBy, createdAt);
        this.sessionId = sessionId;
        this.sourceWalletId = sourceWalletId;
        this.targetWalletId = targetWalletId;
        this.sourceBalanceAfter = sourceBalanceAfter;
        this.targetBalanceAfter = targetBalanceAfter;
    }

    /**
     * Factory method — HT01 bước 4–6. Trừ ví sinh viên TRƯỚC: nếu không đủ điểm thì ném lỗi
     * khi chưa cộng gì cho chủ tài khoản. R06: ví nhận và phí do hệ thống truyền vào, không từ request.
     */
    public static ParkingPayment execute(long sessionId, Wallet studentWallet, Wallet receiverWallet, long fee,
                                         long guardId, UUID idempotencyKey, Instant now) {
        long sourceId = requirePersisted(studentWallet);
        long targetId = requirePersisted(receiverWallet);
        if (sourceId == targetId) {
            throw new IllegalArgumentException("Ví nguồn và ví đích phải khác nhau");
        }
        studentWallet.debit(fee);
        receiverWallet.credit(fee);
        return new ParkingPayment(null, fee, idempotencyKey, guardId, now, sessionId,
                sourceId, targetId, studentWallet.balance(), receiverWallet.balance());
    }

    /** Chỉ dùng khi đọc giao dịch ĐÃ xảy ra từ CSDL lên. */
    public static ParkingPayment restore(long id, long points, UUID idempotencyKey, long performedBy, Instant createdAt,
                                         long sessionId, long sourceWalletId, long targetWalletId,
                                         long sourceBalanceAfter, long targetBalanceAfter) {
        return new ParkingPayment(id, points, idempotencyKey, performedBy, createdAt, sessionId,
                sourceWalletId, targetWalletId, sourceBalanceAfter, targetBalanceAfter);
    }

    @Override
    public TransactionType type() { return TransactionType.THANH_TOAN; }

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

    public long sessionId() { return sessionId; }

    public long sourceWalletId() { return sourceWalletId; }

    public long targetWalletId() { return targetWalletId; }

    public long sourceBalanceAfter() { return sourceBalanceAfter; }

    public long targetBalanceAfter() { return targetBalanceAfter; }
}
