package com.ptit.parking.parking.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Khoản thu tiền mặt trực tiếp tại chốt của bảo vệ khi xe ra (Mục 3.5, 3.6).
 * Tách biệt hoàn toàn khỏi ví điểm và bảng {@code point_transactions} (R12).
 */
public record GateCashCollection(
        Long id,
        long sessionId,
        long guardId,
        Long shiftId,
        long amount,
        boolean overnight,
        UUID idempotencyKey,
        Instant collectedAt
) {

    public GateCashCollection {
        if (amount <= 0) {
            throw new IllegalArgumentException("Số tiền thu phải lớn hơn 0");
        }
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(collectedAt, "collectedAt");
        collectedAt = collectedAt.truncatedTo(ChronoUnit.MICROS);
    }

    public static GateCashCollection create(long sessionId, long guardId, Long shiftId,
                                            long amount, boolean overnight,
                                            UUID idempotencyKey, Instant collectedAt) {
        return new GateCashCollection(null, sessionId, guardId, shiftId, amount, overnight, idempotencyKey, collectedAt);
    }

    public GateCashCollection withId(long newId) {
        return new GateCashCollection(newId, sessionId, guardId, shiftId, amount, overnight, idempotencyKey, collectedAt);
    }
}
