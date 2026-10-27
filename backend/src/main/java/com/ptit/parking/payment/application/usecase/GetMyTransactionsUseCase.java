package com.ptit.parking.payment.application.usecase;

import com.ptit.parking.payment.application.port.out.PointTransactionRepository;
import com.ptit.parking.payment.application.port.out.WalletRepository;
import com.ptit.parking.payment.domain.PointTransaction;
import com.ptit.parking.payment.domain.TransactionType;
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.util.List;

/**
 * SV03, CT01 (Đặc tả API #8) — lịch sử giao dịch của CHÍNH MÌNH, mới nhất trước, phân trang theo khóa.
 * <p>Trả về danh sách {@link PointTransaction} HỖN HỢP (đổi điểm, thanh toán, rút điểm) cùng id ví của người xem:
 * tầng hiển thị gọi {@code changeFor(walletId)} trên từng giao dịch — mỗi loại tự biết ví này được cộng hay bị trừ.
 */
public class GetMyTransactionsUseCase {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 50;

    /** Một trang lịch sử. {@code nextBefore} là giá trị "before" để lấy trang tiếp; null khi đã hết. */
    public record Page(long walletId, List<PointTransaction> items, boolean hasMore, Long nextBefore) {}

    private final WalletRepository wallets;
    private final PointTransactionRepository transactions;

    public GetMyTransactionsUseCase(WalletRepository wallets, PointTransactionRepository transactions) {
        this.wallets = wallets;
        this.transactions = transactions;
    }

    public Page history(long userId, TransactionType type, Long before, int limit) {
        Wallet wallet = wallets.findByUserId(userId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        // Lấy THỪA một dòng: có dòng thứ limit+1 nghĩa là còn dữ liệu cũ hơn (Đặc tả API #8 bước 1)
        List<PointTransaction> rows = transactions.findPageForWallet(wallet.id(), type, before, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<PointTransaction> items = hasMore ? rows.subList(0, limit) : rows;
        Long nextBefore = hasMore ? items.get(items.size() - 1).id() : null;
        return new Page(wallet.id(), items, hasMore, nextBefore);
    }
}