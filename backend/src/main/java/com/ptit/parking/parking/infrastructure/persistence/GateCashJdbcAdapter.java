package com.ptit.parking.parking.infrastructure.persistence;

import com.ptit.parking.parking.application.port.out.GateCashRepository;
import com.ptit.parking.parking.domain.GateCashCollection;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.student.domain.PlateNumber;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

/** Cài đặt {@link GateCashRepository} bằng SQL thuần trên bảng {@code gate_cash_collections}. */
@Component
class GateCashJdbcAdapter implements GateCashRepository {

    private static final String BASE_ENTRY_SELECT = """
            SELECT c.id, c.session_id, v.plate_number, v.vehicle_type_code, s.student_code,
                   su.full_name AS student_name, c.guard_id, gu.full_name AS guard_name,
                   c.shift_id, c.amount, c.overnight, c.collected_at
            FROM gate_cash_collections c
            JOIN parking_sessions ps ON ps.id = c.session_id
            JOIN vehicles v          ON v.id = ps.vehicle_id
            JOIN students s          ON s.user_id = v.student_id
            JOIN users su            ON su.id = s.user_id
            JOIN users gu            ON gu.id = c.guard_id
            """;

    private final JdbcClient jdbc;

    GateCashJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<GateCashCollection> findByIdempotencyKey(UUID key) {
        return jdbc.sql("""
                SELECT id, session_id, guard_id, shift_id, amount, overnight, idempotency_key, collected_at
                FROM gate_cash_collections
                WHERE idempotency_key = :key""")
                .param("key", key.toString())
                .query(GateCashJdbcAdapter::mapCollection)
                .optional();
    }

    @Override
    public GateCashCollection save(GateCashCollection collection) {
        KeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.sql("""
                    INSERT INTO gate_cash_collections
                        (session_id, guard_id, shift_id, amount, overnight, idempotency_key, collected_at)
                    VALUES (:sessionId, :guardId, :shiftId, :amount, :overnight, :key, :collectedAt)""")
                    .param("sessionId", collection.sessionId())
                    .param("guardId", collection.guardId())
                    .param("shiftId", collection.shiftId())
                    .param("amount", collection.amount())
                    .param("overnight", collection.overnight())
                    .param("key", collection.idempotencyKey().toString())
                    .param("collectedAt", LocalDateTime.ofInstant(collection.collectedAt(), ZoneOffset.UTC))
                    .update(keys);
        } catch (DuplicateKeyException e) {
            String msg = String.valueOf(e.getMessage());
            if (msg.contains("uq_gate_cash_session")) {
                throw new DomainException(ErrorCode.SESSION_ALREADY_PAID);
            }
            if (msg.contains("uq_gate_cash_idempotency")) {
                throw new DomainException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            throw e;
        }
        return collection.withId(keys.getKey().longValue());
    }

    @Override
    public List<CashEntry> findByGuardSince(long guardId, Instant from) {
        return jdbc.sql(BASE_ENTRY_SELECT + """
                WHERE c.guard_id = :guardId AND c.collected_at >= :from
                ORDER BY c.collected_at DESC, c.id DESC""")
                .param("guardId", guardId)
                .param("from", LocalDateTime.ofInstant(from, ZoneOffset.UTC))
                .query(GateCashJdbcAdapter::mapEntry)
                .list();
    }

    @Override
    public List<CashEntry> findAllBetween(Instant from, Instant to) {
        return jdbc.sql(BASE_ENTRY_SELECT + """
                WHERE c.collected_at >= :from AND c.collected_at < :to
                ORDER BY c.collected_at DESC, c.id DESC""")
                .param("from", LocalDateTime.ofInstant(from, ZoneOffset.UTC))
                .param("to", LocalDateTime.ofInstant(to, ZoneOffset.UTC))
                .query(GateCashJdbcAdapter::mapEntry)
                .list();
    }

    private static GateCashCollection mapCollection(ResultSet rs, int row) throws SQLException {
        return new GateCashCollection(
                rs.getLong("id"),
                rs.getLong("session_id"),
                rs.getLong("guard_id"),
                rs.getObject("shift_id", Long.class),
                rs.getLong("amount"),
                rs.getBoolean("overnight"),
                UUID.fromString(rs.getString("idempotency_key")),
                rs.getObject("collected_at", LocalDateTime.class).toInstant(ZoneOffset.UTC));
    }

    private static CashEntry mapEntry(ResultSet rs, int row) throws SQLException {
        return new CashEntry(
                rs.getLong("id"),
                rs.getLong("session_id"),
                new PlateNumber(rs.getString("plate_number")),
                rs.getString("vehicle_type_code"),
                rs.getString("student_code"),
                rs.getString("student_name"),
                rs.getLong("guard_id"),
                rs.getString("guard_name"),
                rs.getObject("shift_id", Long.class),
                rs.getLong("amount"),
                rs.getBoolean("overnight"),
                rs.getObject("collected_at", LocalDateTime.class).toInstant(ZoneOffset.UTC));
    }
}
