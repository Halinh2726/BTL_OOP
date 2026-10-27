package com.ptit.parking.payment.application.port.out;

import com.ptit.parking.payment.domain.CashDeposit;
import com.ptit.parking.payment.domain.CashWithdrawal;
import com.ptit.parking.payment.domain.ParkingPayment;
import com.ptit.parking.payment.domain.PointTransaction;
import com.ptit.parking.payment.domain.TransactionType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Sổ giao dịch GIẢ trong bộ nhớ, dùng chung cho test các use case cộng/trừ điểm. Giống CSDL thật ở các điểm
 * quan trọng: gán id khi lưu, chặn trùng Idempotency-Key, lịch sử lọc theo ví và sắp id giảm dần.
 */
public class InMemoryPointTransactionRepository implements PointTransactionRepository {

    private final List<Recorded> saved = new ArrayList<>();
    private long nextId = 1;
    private Runnable beforeNextSave = () -> { };

    @Override
    public long save(PointTransaction transaction, String requestHash) {
        Runnable hook = beforeNextSave;
        beforeNextSave = () -> { };
        hook.run();
        if (findByIdempotencyKey(transaction.idempotencyKey()).isPresent()) {
            throw new DuplicateIdempotencyKeyException(null);         // như UNIQUE uq_point_transactions_idempotency
        }
        long id = nextId++;
        saved.add(new Recorded(withId(transaction, id), requestHash));
        return id;
    }

    @Override
    public Optional<Recorded> findByIdempotencyKey(UUID idempotencyKey) {
        return saved.stream().filter(r -> r.transaction().idempotencyKey().equals(idempotencyKey)).findFirst();
    }

    @Override
    public List<PointTransaction> findPageForWallet(long walletId, TransactionType type, Long before, int limit) {
        return saved.stream().map(Recorded::transaction)
                .filter(t -> t.involves(walletId))
                .filter(t -> type == null || t.type() == type)
                .filter(t -> before == null || t.id() < before)
                .sorted(Comparator.comparing(PointTransaction::id).reversed())
                .limit(limit)
                .toList();
    }

    /** Mọi giao dịch đã lưu, theo thứ tự lưu. */
    public List<PointTransaction> all() {
        return saved.stream().map(Recorded::transaction).toList();
    }

    /** Giả lập một request SONG SONG chen vào ghi trước, ngay trước lần save kế tiếp. */
    public void beforeNextSave(Runnable action) {
        this.beforeNextSave = action;
    }

    /** "CSDL gán id": dựng lại đúng lớp con kèm id qua factory restore. */
    private static PointTransaction withId(PointTransaction t, long id) {
        return switch (t) {
            case CashDeposit d -> CashDeposit.restore(id, d.points(), d.idempotencyKey(), d.performedBy(),
                    d.createdAt(), d.targetWalletId(), d.targetBalanceAfter(), d.cashAmount(), d.rate());
            case CashWithdrawal w -> CashWithdrawal.restore(id, w.points(), w.idempotencyKey(), w.performedBy(),
                    w.createdAt(), w.sourceWalletId(), w.sourceBalanceAfter(), w.cashAmount(), w.rate());
            case ParkingPayment p -> ParkingPayment.restore(id, p.points(), p.idempotencyKey(), p.performedBy(),
                    p.createdAt(), p.sessionId(), p.sourceWalletId(), p.targetWalletId(),
                    p.sourceBalanceAfter(), p.targetBalanceAfter());
        };
    }
}