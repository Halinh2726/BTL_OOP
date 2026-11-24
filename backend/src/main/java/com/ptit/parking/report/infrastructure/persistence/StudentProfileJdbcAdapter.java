package com.ptit.parking.report.infrastructure.persistence;

import com.ptit.parking.report.application.port.out.StudentProfileQuery;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Cài đặt {@link StudentProfileQuery} bằng các câu SELECT trên users / students / wallets / vehicles / parking_sessions. */
@Component
class StudentProfileJdbcAdapter implements StudentProfileQuery {

    private final JdbcClient jdbc;

    StudentProfileJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<StudentRow> search(String fragment, int limit) {
        // Ký tự đại diện của LIKE trong chuỗi người dùng gõ được thoát để tìm đúng nghĩa đen
        String pattern = "%" + fragment.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return jdbc.sql("""
                SELECT u.id, s.student_code, u.full_name, u.status, COALESCE(w.balance, 0) AS balance,
                       (SELECT COUNT(*) FROM vehicles v WHERE v.student_id = u.id AND v.status = 'DANG_DUNG') AS active_vehicles,
                       EXISTS (SELECT 1 FROM parking_sessions p JOIN vehicles v ON v.id = p.vehicle_id
                               WHERE v.student_id = u.id AND p.status = 'DANG_GUI') AS parked_now
                FROM students s
                JOIN users u        ON u.id = s.user_id
                LEFT JOIN wallets w ON w.user_id = u.id
                WHERE s.student_code LIKE :pattern OR u.full_name LIKE :pattern
                ORDER BY s.student_code
                LIMIT :limit""")
                .param("pattern", pattern)
                .param("limit", limit)
                .query((rs, row) -> new StudentRow(rs.getLong("id"), rs.getString("student_code"),
                        rs.getString("full_name"), rs.getString("status"), rs.getLong("balance"),
                        rs.getInt("active_vehicles"), rs.getBoolean("parked_now")))
                .list();
    }

    @Override
    public Optional<StudentDetail> findByCode(String studentCode) {
        return jdbc.sql("""
                SELECT u.id, s.student_code, u.full_name, u.email, s.phone, u.status, u.created_at,
                       COALESCE(w.balance, 0) AS balance
                FROM students s
                JOIN users u        ON u.id = s.user_id
                LEFT JOIN wallets w ON w.user_id = u.id
                WHERE s.student_code = :code""")
                .param("code", studentCode)
                .query((rs, row) -> new StudentDetail(rs.getLong("id"), rs.getString("student_code"),
                        rs.getString("full_name"), rs.getString("email"), rs.getString("phone"), rs.getString("status"),
                        SqlTime.instant(rs, "created_at"), rs.getLong("balance")))
                .optional();
    }

    @Override
    public List<VehicleRow> vehiclesOf(long userId) {
        return jdbc.sql("""
                SELECT v.id, v.plate_number, t.name AS type_name, v.status, v.registered_at,
                       EXISTS (SELECT 1 FROM parking_sessions p WHERE p.vehicle_id = v.id AND p.status = 'DANG_GUI') AS parked_now
                FROM vehicles v
                JOIN vehicle_types t ON t.code = v.vehicle_type_code
                WHERE v.student_id = :user
                ORDER BY v.status, v.registered_at DESC""")
                .param("user", userId)
                .query((rs, row) -> new VehicleRow(rs.getLong("id"), rs.getString("plate_number"),
                        rs.getString("type_name"), rs.getString("status"), SqlTime.instant(rs, "registered_at"),
                        rs.getBoolean("parked_now")))
                .list();
    }

    @Override
    public List<SessionRow> recentSessions(long userId, int limit) {
        return jdbc.sql("""
                SELECT p.id, v.plate_number, p.entered_at, p.exited_at, t.points AS fee
                FROM parking_sessions p
                JOIN vehicles v                           ON v.id = p.vehicle_id
                LEFT JOIN parking_payment_details d       ON d.session_id = p.id
                LEFT JOIN point_transactions t            ON t.id = d.transaction_id
                WHERE v.student_id = :user
                ORDER BY p.entered_at DESC
                LIMIT :limit""")
                .param("user", userId)
                .param("limit", limit)
                .query((rs, row) -> new SessionRow(rs.getLong("id"), rs.getString("plate_number"),
                        SqlTime.instant(rs, "entered_at"), SqlTime.instant(rs, "exited_at"),
                        rs.getObject("fee", Long.class)))
                .list();
    }
}
