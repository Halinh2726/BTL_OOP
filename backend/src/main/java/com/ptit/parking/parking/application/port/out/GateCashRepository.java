package com.ptit.parking.parking.application.port.out;

import com.ptit.parking.parking.domain.GateCashCollection;
import com.ptit.parking.student.domain.PlateNumber;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port lưu và tra cứu các khoản thu tiền mặt tại cổng (gate_cash_collections — append-only). */
public interface GateCashRepository {

    record CashEntry(
            long collectionId,
            long sessionId,
            PlateNumber plate,
            String vehicleTypeCode,
            String studentCode,
            String fullName,
            long guardId,
            String guardName,
            Long shiftId,
            long amount,
            boolean overnight,
            Instant collectedAt
    ) {}

    Optional<GateCashCollection> findByIdempotencyKey(UUID key);

    GateCashCollection save(GateCashCollection collection);

    List<CashEntry> findByGuardSince(long guardId, Instant from);

    List<CashEntry> findAllBetween(Instant from, Instant to);
}
