package com.ptit.parking.shared.web;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Header Idempotency-Key của các API thay đổi điểm (Đặc tả API mục 6): bắt buộc, là UUID do giao diện sinh
 * khi mở bước xác nhận. Kết quả trả lại cho request gửi lặp mang header {@code Idempotent-Replayed: true}.
 */
public final class IdempotencyKeyHeader {

    public static final String NAME = "Idempotency-Key";
    public static final String REPLAYED = "Idempotent-Replayed";

    private IdempotencyKeyHeader() {
    }

    /** Thiếu hoặc không phải UUID → IDEMPOTENCY_KEY_REQUIRED (400). */
    public static UUID parse(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new DomainException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        try {
            return UUID.fromString(rawValue.strip());
        } catch (IllegalArgumentException e) {
            throw new DomainException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
    }

    /** 201 Created; nếu là kết quả cũ trả lại thì kèm header Idempotent-Replayed: true. */
    public static <T> ResponseEntity<T> created(T body, boolean replayed) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.CREATED);
        if (replayed) {
            response.header(REPLAYED, "true");
        }
        return response.body(body);
    }
}