package com.ptit.parking.gate.infrastructure.persistence;

import com.ptit.parking.gate.application.port.out.ShiftRepository;
import com.ptit.parking.gate.domain.CashOnHand;
import com.ptit.parking.gate.domain.GuardShift;
import com.ptit.parking.gate.domain.ShiftSummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

/** Cài đặt {@link ShiftRepository} bằng SQL thuần. Thời điểm lưu theo UTC. */
@Component
class ShiftJdbcAdapter implements ShiftRepository {

    private static final String SELECT = """
            SELECT id, guard_id, started_at, opening_cash, expected_opening_cash, ended_at, check_ins, check_outs,
                   vehicles_in_lot, open_incidents, closing_cash, handover_note, received_by, received_at
            FROM guard_shifts
            """;

    private final JdbcClient jdbc;

    ShiftJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<GuardShift> findOpenByGuard(long guardId) {
        return jdbc.sql(SELECT + "WHERE open_guard_id = :guardId")
                .param("guardId", guardId).query(ShiftJdbcAdapter::map).optional();
    }

    @Override
    public Optional<GuardShift> lockOpenByGuard(long guardId) {
        return jdbc.sql(SELECT + "WHERE open_guard_id = :guardId FOR UPDATE")
                .param("guardId", guardId).query(ShiftJdbcAdapter::map).optional();
    }

    @Override
    public List<GuardShift> findPendingHandovers(long excludingGuardId, boolean lock) {
        return jdbc.sql(SELECT + """
                        WHERE ended_at IS NOT NULL AND received_by IS NULL AND guard_id <> :guardId
                        ORDER BY ended_at DESC, id DESC
                        """ + (lock ? "FOR UPDATE" : ""))
                .param("guardId", excludingGuardId).query(ShiftJdbcAdapter::map).list();
    }

    @Override
    public List<GuardShift> findByGuard(long guardId, int limit) {
        return jdbc.sql(SELECT + "WHERE guard_id = :guardId ORDER BY started_at DESC, id DESC LIMIT :limit")
                .param("guardId", guardId).param("limit", limit).query(ShiftJdbcAdapter::map).list();
    }

    @Override
    public List<GuardShift> findStartedBetween(Instant from, Instant to, Long guardId) {
        return jdbc.sql(SELECT + """
                        WHERE started_at >= :from AND started_at < :to AND (:guardId IS NULL OR guard_id = :guardId)
                        ORDER BY started_at, id""")
                .param("from", utc(from)).param("to", utc(to)).param("guardId", guardId)
                .query(ShiftJdbcAdapter::map).list();
    }

    @Override
    public GuardShift save(GuardShift shift) {
        if (shift.id() == null) {
            KeyHolder keys = new GeneratedKeyHolder();
            jdbc.sql("""
                    INSERT INTO guard_shifts (guard_id, started_at, opening_cash, expected_opening_cash)
                    VALUES (:guardId, :startedAt, :openingCash, :expected)""")
                    .param("guardId", shift.guardId())
                    .param("startedAt", utc(shift.startedAt()))
                    .param("openingCash", shift.openingCash().amount())
                    .param("expected", shift.expectedOpeningCash() == null ? null : shift.expectedOpeningCash().amount())
                    .update(keys);
            return shift.withId(keys.getKey().longValue());
        }
        ShiftSummary summary = shift.summary();
        jdbc.sql("""
                UPDATE guard_shifts
                SET ended_at = :endedAt, check_ins = :checkIns, check_outs = :checkOuts,
                    vehicles_in_lot = :inLot, open_incidents = :openIncidents, closing_cash = :closingCash,
                    handover_note = :note, received_by = :receivedBy, received_at = :receivedAt
                WHERE id = :id""")
                .param("endedAt", utc(shift.endedAt()))
                .param("checkIns", summary == null ? null : summary.checkIns())
                .param("checkOuts", summary == null ? null : summary.checkOuts())
                .param("inLot", summary == null ? null : summary.vehiclesInLot())
                .param("openIncidents", summary == null ? null : summary.openIncidents())
                .param("closingCash", shift.closingCash() == null ? null : shift.closingCash().amount())
                .param("note", shift.handoverNote())
                .param("receivedBy", shift.receivedBy())
                .param("receivedAt", utc(shift.receivedAt()))
                .param("id", shift.id())
                .update();
        return shift;
    }

    private static GuardShift map(ResultSet rs, int row) throws SQLException {
        Instant endedAt = instant(rs, "ended_at");
        ShiftSummary summary = endedAt == null ? null : new ShiftSummary(rs.getInt("check_ins"),
                rs.getInt("check_outs"), rs.getInt("vehicles_in_lot"), rs.getInt("open_incidents"));
        Long expected = rs.getObject("expected_opening_cash", Long.class);
        Long closing = rs.getObject("closing_cash", Long.class);
        return GuardShift.restore(rs.getLong("id"), rs.getLong("guard_id"), instant(rs, "started_at"),
                new CashOnHand(rs.getLong("opening_cash")), expected == null ? null : new CashOnHand(expected),
                endedAt, summary, closing == null ? null : new CashOnHand(closing), rs.getString("handover_note"),
                rs.getObject("received_by", Long.class), instant(rs, "received_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static LocalDateTime utc(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
