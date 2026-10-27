package com.ptit.parking.shared.ratelimit;

import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Duration;

/**
 * Vượt giới hạn tần suất (Đặc tả API mục 11) → 429 TOO_MANY_REQUESTS kèm header Retry-After.
 * <p>Không dùng DomainException: đây không phải quy tắc nghiệp vụ, và lỗi này mang thêm dữ liệu
 * (thời gian chờ) mà GlobalExceptionHandler cần để ghi header.
 */
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(Duration retryAfter) {
        super(ErrorCode.TOO_MANY_REQUESTS.defaultMessage());
        this.retryAfterSeconds = toSeconds(retryAfter);
    }

    public long retryAfterSeconds() { return retryAfterSeconds; }

    /** Làm tròn LÊN theo giây, tối thiểu 1: chờ đúng số giây này là chắc chắn được thử lại. */
    public static long toSeconds(Duration wait) {
        return Math.max(1, (wait.toMillis() + 999) / 1000);
    }
}
