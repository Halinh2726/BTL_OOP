package com.ptit.parking.payment.application;

import com.ptit.parking.payment.application.port.out.DuplicateIdempotencyKeyException;
import com.ptit.parking.payment.application.port.out.PointTransactionRepository;
import com.ptit.parking.payment.application.port.out.PointTransactionRepository.Recorded;
import com.ptit.parking.payment.domain.PointTransaction;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Chống xử lý trùng (Đặc tả API mục 6) cho MỌI API thay đổi điểm: đổi điểm, rút điểm, thanh toán khi xe ra.
 * Mỗi Idempotency-Key chỉ tạo ra NHIỀU NHẤT một giao dịch — quản lý bấm "Xác nhận" hai lần, mạng chập chờn
 * gửi lại, hay hai request song song đều không làm cộng/trừ điểm lần hai.
 * <ul>
 *   <li>Key mới → chạy nghiệp vụ trong MỘT transaction, lưu giao dịch.</li>
 *   <li>Key đã dùng, cùng nội dung → KHÔNG chạy lại; trả lại giao dịch cũ (replayed).</li>
 *   <li>Key đã dùng, khác nội dung → IDEMPOTENCY_KEY_REUSED.</li>
 * </ul>
 */
public class IdempotencyGuard {

    /** Giao dịch kết quả và cho biết đây là kết quả CŨ được trả lại hay giao dịch vừa tạo. */
    public record Execution<T extends PointTransaction>(T transaction, boolean replayed) {}

    private final PointTransactionRepository transactions;
    private final TransactionRunner transactionRunner;

    public IdempotencyGuard(PointTransactionRepository transactions, TransactionRunner transactionRunner) {
        this.transactions = transactions;
        this.transactionRunner = transactionRunner;
    }

    /**
     * @param work nghiệp vụ chạy BÊN TRONG transaction: khóa ví, cộng/trừ điểm, lưu ví, rồi trả về giao dịch
     *             (chưa lưu) — guard lưu giao dịch và trả về bản đã lưu (có id).
     */
    public <T extends PointTransaction> Execution<T> execute(UUID idempotencyKey, String requestHash,
                                                              Class<T> type, Supplier<T> work) {
        Optional<Recorded> previous = transactions.findByIdempotencyKey(idempotencyKey);
        if (previous.isPresent()) {
            return replay(previous.get(), requestHash, type);
        }
        try {
            return transactionRunner.inTransaction(() -> {
                transactions.save(work.get(), requestHash);
                Recorded saved = transactions.findByIdempotencyKey(idempotencyKey).orElseThrow();
                return new Execution<>(type.cast(saved.transaction()), false);
            });
        } catch (DuplicateIdempotencyKeyException e) {
            // Request song song cùng key đã ghi trước; transaction của ta đã rollback toàn bộ -> trả lại kết quả của nó
            return replay(transactions.findByIdempotencyKey(idempotencyKey).orElseThrow(), requestHash, type);
        }
    }

    private static <T extends PointTransaction> Execution<T> replay(Recorded recorded, String requestHash, Class<T> type) {
        if (!recorded.requestHash().equals(requestHash) || !type.isInstance(recorded.transaction())) {
            throw new DomainException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        return new Execution<>(type.cast(recorded.transaction()), true);
    }
}