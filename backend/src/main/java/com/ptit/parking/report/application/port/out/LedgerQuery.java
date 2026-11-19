package com.ptit.parking.report.application.port.out;

import com.ptit.parking.payment.domain.TransactionType;
import java.time.Instant;
import java.util.List;

/**
 * Port CHỈ ĐỌC: sổ giao dịch toàn hệ thống cho quản lý — mỗi dòng đã ghép sẵn tên người chuyển / nhận / thực hiện,
 * số tiền mặt, biển số (thanh toán), lý do (điều chỉnh). Đây là "read model" phục vụ tra cứu, không phải domain:
 * ghi giao dịch vẫn đi qua PointTransactionRepository của module payment.
 */
public interface LedgerQuery {

    /**
     * Điều kiện lọc; mọi trường đều có thể null (= không lọc).
     *
     * @param from        từ thời điểm (gồm)
     * @param to          đến thời điểm (không gồm)
     * @param studentCode sinh viên là bên chuyển HOẶC bên nhận
     * @param before      phân trang theo khóa: chỉ lấy id nhỏ hơn
     */
    record Filter(Instant from, Instant to, TransactionType type, String studentCode, Long before, int limit) {}

    /** Một bên của giao dịch (ví nguồn hoặc ví đích). {@code studentCode} null nếu không phải sinh viên. */
    record Party(String fullName, String studentCode, String roleCode) {}

    /** {@code from} null với đổi điểm (tiền mặt vào), {@code to} null với rút điểm (tiền mặt ra). */
    record Entry(long id, TransactionType type, long points, Long cashAmount, Party from, Party to,
                 String performerName, String performerRole, Instant createdAt, String plateNumber, String reason,
                 Long originalTransactionId) {}

    /** Mới nhất trước (id giảm dần), tối đa {@code filter.limit()} dòng. */
    List<Entry> find(Filter filter);
}
