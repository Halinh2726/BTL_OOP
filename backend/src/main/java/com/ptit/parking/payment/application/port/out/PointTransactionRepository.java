package com.ptit.parking.payment.application.port.out;

import com.ptit.parking.payment.domain.PointTransaction;
import com.ptit.parking.payment.domain.TransactionType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port: sổ giao dịch điểm (point_transactions + bảng chi tiết). Chỉ THÊM và ĐỌC — không có sửa/xóa (R10).
 * <p>Làm việc với kiểu cha {@link PointTransaction}: lưu hay đọc lên đều có thể là CashDeposit,
 * CashWithdrawal hoặc ParkingPayment — nơi gọi không cần biết.
 */
public interface PointTransactionRepository {

    /** Giao dịch đã lưu kèm "dấu vân tay" nội dung request — dùng để chống xử lý trùng (Idempotency-Key). */
    record Recorded(PointTransaction transaction, String requestHash) {}

    /** Lưu giao dịch MỚI kèm bảng chi tiết của đúng loại; trả về id vừa sinh. */
    long save(PointTransaction transaction, String requestHash);

    Optional<Recorded> findByIdempotencyKey(UUID idempotencyKey);

    /**
     * Một trang lịch sử của MỘT ví (ví là nguồn hoặc đích), id giảm dần.
     *
     * @param type   chỉ lấy loại này; {@code null} = mọi loại
     * @param before chỉ lấy id nhỏ hơn giá trị này (phân trang theo khóa); {@code null} = trang đầu
     */
    List<PointTransaction> findPageForWallet(long walletId, TransactionType type, Long before, int limit);
}