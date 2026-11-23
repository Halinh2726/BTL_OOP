package com.ptit.parking.student.adapter.out.persistence;

import com.ptit.parking.student.application.port.out.StudentSessionQuery;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Cài đặt {@link StudentSessionQuery} đọc lịch sử gửi xe của sinh viên từ CSDL. */
@Component
class StudentSessionJdbcAdapter implements StudentSessionQuery {

    private final JdbcClient jdbc;

    StudentSessionJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SessionRecord> findByStudent(long studentId, int limit) {
        return jdbc.sql("""
                SELECT p.id,
                       v.plate_number,
                       v.vehicle_type_code,
                       p.entered_at,
                       p.exited_at,
                       t.points AS fee,
                       t.id     AS payment_id,
                       eg.full_name AS entry_guard_name,
                       xg.full_name AS exit_guard_name
                FROM parking_sessions p
                JOIN vehicles v                     ON v.id = p.vehicle_id
                LEFT JOIN users eg                  ON eg.id = p.entry_guard_id
                LEFT JOIN users xg                  ON xg.id = p.exit_guard_id
                LEFT JOIN parking_payment_details d ON d.session_id = p.id
                LEFT JOIN point_transactions t      ON t.id = d.transaction_id
                WHERE v.student_id = :studentId
                ORDER BY (p.exited_at IS NULL) DESC, p.entered_at DESC
                LIMIT :limit""")
                .param("studentId", studentId)
                .param("limit", limit)
                .query(StudentSessionJdbcAdapter::map)
                .list();
    }

    private static SessionRecord map(ResultSet rs, int row) throws SQLException {
        return new SessionRecord(
                rs.getLong("id"),
                rs.getString("plate_number"),
                rs.getString("vehicle_type_code"),
                instant(rs, "entered_at"),
                instant(rs, "exited_at"),
                rs.getObject("fee", Long.class),
                rs.getObject("payment_id", Long.class),
                rs.getString("entry_guard_name"),
                rs.getString("exit_guard_name"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
