package com.ptit.parking.guest.domain;

import java.time.Instant;
import java.util.UUID;

public record GuestPayment(long sessionId, long guardId, Long shiftId, Method method, long feePoints,
                           long amount, boolean overnight, long lostTicketFee, UUID idempotencyKey, Instant paidAt) {
    public enum Method { TIEN_MAT, VIETQR }
}
