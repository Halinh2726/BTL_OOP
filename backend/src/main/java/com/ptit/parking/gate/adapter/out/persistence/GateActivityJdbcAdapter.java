package com.ptit.parking.gate.adapter.out.persistence;

import com.ptit.parking.gate.application.port.out.GateActivityQuery;
import com.ptit.parking.gate.domain.ParkedVehicle;
import com.ptit.parking.student.domain.PlateNumber;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Cài đặt {@link GateActivityQuery}: các câu SELECT trên parking_sessions + vehicles + students + users. */
@Component
class GateActivityJdbcAdapter implements GateActivityQuery {

    private final JdbcClient jdbc;

    GateActivityJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ParkedVehicle> parkedVehicles() {
        return jdbc.sql("""
                SELECT ps.id, v.plate_number, v.vehicle_type_code, s.student_code, u.full_name,
                       ps.entered_at, g.full_name AS guard_name
                FROM parking_sessions ps
                JOIN vehicles v ON v.id = ps.vehicle_id
                JOIN students s ON s.user_id = v.student_id
                JOIN users u ON u.id = s.user_id
                JOIN users g ON g.id = ps.entry_guard_id
                WHERE ps.status = 'DANG_GUI'
                ORDER BY ps.entered_at, ps.id""")
                .query((rs, row) -> new ParkedVehicle(rs.getLong("id"), new PlateNumber(rs.getString("plate_number")),
                        rs.getString("vehicle_type_code"), rs.getString("student_code"), rs.getString("full_name"),
                        rs.getObject("entered_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                        rs.getString("guard_name")))
                .list();
    }

    @Override
    public long countParked() {
        return jdbc.sql("SELECT COUNT(*) FROM parking_sessions WHERE status = 'DANG_GUI'").query(Long.class).single();
    }

    @Override
    public long countCheckIns(long guardId, Instant from, Instant to) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM parking_sessions
                WHERE entry_guard_id = :guardId AND entered_at >= :from AND entered_at <= :to""")
                .param("guardId", guardId).param("from", utc(from)).param("to", utc(to))
                .query(Long.class).single();
    }

    @Override
    public long countCheckOuts(long guardId, Instant from, Instant to) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM parking_sessions
                WHERE exit_guard_id = :guardId AND exited_at >= :from AND exited_at <= :to""")
                .param("guardId", guardId).param("from", utc(from)).param("to", utc(to))
                .query(Long.class).single();
    }

    /** Hai nhánh UNION ALL: lượt mình cho VÀO và lượt mình cho RA — một lượt có thể xuất hiện ở cả hai. */
    @Override
    public List<ActivityEntry> activityOf(long guardId, Instant from, int limit) {
        return jdbc.sql("""
                SELECT * FROM (
                    SELECT ps.id, v.plate_number, v.vehicle_type_code, s.student_code, u.full_name,
                           'VAO' AS direction, ps.entered_at AS at
                    FROM parking_sessions ps
                    JOIN vehicles v ON v.id = ps.vehicle_id
                    JOIN students s ON s.user_id = v.student_id
                    JOIN users u ON u.id = s.user_id
                    WHERE ps.entry_guard_id = :guardId AND ps.entered_at >= :from
                    UNION ALL
                    SELECT ps.id, v.plate_number, v.vehicle_type_code, s.student_code, u.full_name,
                           'RA' AS direction, ps.exited_at AS at
                    FROM parking_sessions ps
                    JOIN vehicles v ON v.id = ps.vehicle_id
                    JOIN students s ON s.user_id = v.student_id
                    JOIN users u ON u.id = s.user_id
                    WHERE ps.exit_guard_id = :guardId AND ps.exited_at >= :from
                ) activity
                ORDER BY at DESC, id DESC
                LIMIT :limit""")
                .param("guardId", guardId).param("from", utc(from)).param("limit", limit)
                .query((rs, row) -> new ActivityEntry(rs.getLong("id"), new PlateNumber(rs.getString("plate_number")),
                        rs.getString("vehicle_type_code"), rs.getString("student_code"), rs.getString("full_name"),
                        Direction.valueOf(rs.getString("direction")),
                        rs.getObject("at", LocalDateTime.class).toInstant(ZoneOffset.UTC)))
                .list();
    }

    @Override
    public Map<Long, String> namesOf(Collection<Long> userIds) {
        Map<Long, String> names = new HashMap<>();
        if (userIds.isEmpty()) {
            return names;
        }
        jdbc.sql("SELECT id, full_name FROM users WHERE id IN (:ids)")
                .param("ids", List.copyOf(userIds))
                .query((rs, row) -> names.put(rs.getLong("id"), rs.getString("full_name")))
                .list();
        return names;
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
