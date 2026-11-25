package com.ptit.parking.support.infrastructure.persistence;

import com.ptit.parking.support.application.port.out.ComplaintDirectory;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Cài đặt {@link ComplaintDirectory} bằng các câu SELECT trên users / students / parking_sessions / giao dịch. */
@Component
class ComplaintDirectoryJdbcAdapter implements ComplaintDirectory {

    private static final String SESSION_SELECT = """
            SELECT p.id, v.student_id, v.plate_number, p.entered_at, p.exited_at, t.points AS fee, t.id AS payment_id
            FROM parking_sessions p
            JOIN vehicles v                     ON v.id = p.vehicle_id
            LEFT JOIN parking_payment_details d ON d.session_id = p.id
            LEFT JOIN point_transactions t      ON t.id = d.transaction_id
            """;

    private final JdbcClient jdbc;

    ComplaintDirectoryJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<Long, String> namesOf(Collection<Long> userIds) {
        Map<Long, String> names = new HashMap<>();
        if (!userIds.isEmpty()) {
            jdbc.sql("SELECT id, full_name FROM users WHERE id IN (:ids)").param("ids", List.copyOf(userIds))
                    .query((rs, row) -> names.put(rs.getLong("id"), rs.getString("full_name"))).list();
        }
        return names;
    }

    @Override
    public Map<Long, StudentInfo> studentsOf(Collection<Long> userIds) {
        Map<Long, StudentInfo> students = new HashMap<>();
        if (!userIds.isEmpty()) {
            jdbc.sql("SELECT u.id, s.student_code, u.full_name FROM users u JOIN students s ON s.user_id = u.id "
                            + "WHERE u.id IN (:ids)")
                    .param("ids", List.copyOf(userIds))
                    .query((rs, row) -> students.put(rs.getLong("id"),
                            new StudentInfo(rs.getLong("id"), rs.getString("student_code"), rs.getString("full_name"))))
                    .list();
        }
        return students;
    }

    @Override
    public Map<Long, SessionInfo> sessionsOf(Collection<Long> sessionIds) {
        Map<Long, SessionInfo> sessions = new HashMap<>();
        if (!sessionIds.isEmpty()) {
            jdbc.sql(SESSION_SELECT + "WHERE p.id IN (:ids)").param("ids", List.copyOf(sessionIds))
                    .query((rs, row) -> {
                        SessionInfo info = session(rs);
                        sessions.put(info.id(), info);
                        return info;
                    })
                    .list();
        }
        return sessions;
    }

    @Override
    public Optional<SessionInfo> sessionOfStudent(long sessionId, long studentId) {
        return jdbc.sql(SESSION_SELECT + "WHERE p.id = :id AND v.student_id = :student")
                .param("id", sessionId).param("student", studentId)
                .query((rs, row) -> session(rs)).optional();
    }

    @Override
    public List<SessionInfo> recentSessions(long studentId, int limit) {
        return jdbc.sql(SESSION_SELECT + "WHERE v.student_id = :student ORDER BY p.entered_at DESC LIMIT :limit")
                .param("student", studentId).param("limit", limit)
                .query((rs, row) -> session(rs)).list();
    }

    private static SessionInfo session(ResultSet rs) throws SQLException {
        return new SessionInfo(rs.getLong("id"), rs.getLong("student_id"), rs.getString("plate_number"),
                instant(rs, "entered_at"), instant(rs, "exited_at"), rs.getObject("fee", Long.class),
                rs.getObject("payment_id", Long.class));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
