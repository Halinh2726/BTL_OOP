package com.ptit.parking.payment.domain;

import java.time.Instant;
import java.util.UUID;

/** GiaoDichDoiDiem (QL01) — quản lý nhận tiền mặt, CỘNG điểm vào ví sinh viên. Chỉ có ví đích. */
public final class CashDeposit extends PointTransaction {

    private final long targetWalletId;
    private final long targetBalanceAfter;
    private final long cashAmount;
    private final ExchangeRate rate;       // R11: lưu tỷ lệ TẠI THỜI ĐIỂM giao dịch

    private CashDeposit(Long id, long points, UUID idempotencyKey, long performedBy, Instant createdAt,
                        long targetWalletId, long targetBalanceAfter, long cashAmount, ExchangeRate rate) {
        super(id, points, idempotencyKey, performedBy, createdAt);
        this.targetWalletId = targetWalletId;
        this.targetBalanceAfter = targetBalanceAfter;
        this.cashAmount = cashAmount;
        this.rate = rate;
    }

    /**
     * Factory method: cách DUY NHẤT tạo một giao dịch đổi điểm mới.
     * Thực hiện nghiệp vụ (cộng điểm) TRƯỚC khi đối tượng tồn tại -> không thể có
     * một CashDeposit "nửa vời" chưa thật sự cộng điểm.
     */
    public static CashDeposit execute(Wallet studentWallet, long cashAmount, ExchangeRate rate,
                                      long managerId, UUID idempotencyKey, Instant now) {
        long points = rate.pointsFor(cashAmount);          // R02
        studentWallet.credit(points);
        return new CashDeposit(null, points, idempotencyKey, managerId, now,
                requirePersisted(studentWallet), studentWallet.balance(), cashAmount, rate);
    }

    /** Chỉ dùng khi đọc giao dịch ĐÃ xảy ra từ CSDL lên. */
    public static CashDeposit restore(long id, long points, UUID idempotencyKey, long performedBy, Instant createdAt,
                                      long targetWalletId, long targetBalanceAfter, long cashAmount, ExchangeRate rate) {
        return new CashDeposit(id, points, idempotencyKey, performedBy, createdAt,
                targetWalletId, targetBalanceAfter, cashAmount, rate);
    }

    @Override
    public TransactionType type() { return TransactionType.DOI_DIEM; }

    @Override
    public long changeFor(long walletId) {
        return walletId == targetWalletId ? points() : 0;
    }

    @Override
    public long balanceAfterFor(long walletId) {
        if (walletId != targetWalletId) {
            throw notInvolved(walletId);
        }
        return targetBalanceAfter;
    }

    public long targetWalletId() { return targetWalletId; }

    public long targetBalanceAfter() { return targetBalanceAfter; }

    public long cashAmount() { return cashAmount; }

    public ExchangeRate rate() { return rate; }
}
