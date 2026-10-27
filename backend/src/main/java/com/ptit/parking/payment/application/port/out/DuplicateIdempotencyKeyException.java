package com.ptit.parking.payment.application.port.out;

/**
 * Hai request CÙNG Idempotency-Key chạy song song: request đến sau bị ràng buộc UNIQUE của CSDL chặn khi lưu.
 * Adapter đổi lỗi CSDL thành exception này để tầng ứng dụng xử lý mà không phụ thuộc Spring/JDBC.
 */
public class DuplicateIdempotencyKeyException extends RuntimeException {

    public DuplicateIdempotencyKeyException(Throwable cause) {
        super("Idempotency-Key đã được dùng bởi một giao dịch khác vừa ghi", cause);
    }
}