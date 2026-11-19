package com.ptit.parking.report.adapter.out.persistence;

import com.ptit.parking.payment.domain.TransactionType;
import com.ptit.parking.report.application.port.out.LedgerQuery;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Cài đặt {@link LedgerQuery}: một câu SELECT ghép sổ giao dịch với ví → người dùng → MSSV, người thực hiện, tiền mặt,
 * biển số của lượt gửi, lý do điều chỉnh. Điều kiện WHERE chỉ thêm khi có lọc (dùng được chỉ mục).
 */
@Component
class LedgerJdbcAdapter implements LedgerQuery {

    private static final String SELECT = """
            SELECT t.id, t.type, t.points, t.created_at, c.cash_amount,
                   su.full_name AS src_name, ss.student_code AS src_code, su.role_code AS src_role,
                   tu.full_name AS dst_name, ts.student_code AS dst_code, tu.role_code AS dst_role,
                   pu.full_name AS performer_name, pu.role_code AS performer_role,
                   v.plate_number, a.reason, a.original_transaction_id
            FROM point_transactions t
            JOIN users pu                        ON pu.id = t.performed_by
            LEFT JOIN cash_details c             ON c.transaction_id = t.id
            LEFT JOIN wallets sw                 ON sw.id = t.source_wallet_id
            LEFT JOIN users su                   ON su.id = sw.user_id
            LEFT JOIN students ss                ON ss.user_id = su.id
            LEFT JOIN wallets tw                 ON tw.id = t.target_wallet_id
            LEFT JOIN users tu                   ON tu.id = tw.user_id
            LEFT JOIN students ts                ON ts.user_id = tu.id
            LEFT JOIN parking_payment_details pp ON pp.transaction_id = t.id
            LEFT JOIN parking_sessions s         ON s.id = pp.session_id
            LEFT JOIN vehicles v                 ON v.id = s.vehicle_id
            LEFT JOIN adjustment_details a       ON a.transaction_id = t.id
            """;

    private final JdbcClient jdbc;

    LedgerJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Entry> find(Filter filter) {
        List<String> where = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        if (filter.from() != null) {
            where.add("t.created_at >= :from");
            params.put("from", SqlTime.utc(filter.from()));
        }
        if (filter.to() != null) {
            where.add("t.created_at < :to");
            params.put("to", SqlTime.utc(filter.to()));
        }
        if (filter.type() != null) {
            where.add("t.type = :type");
            params.put("type", filter.type().name());
        }
        if (filter.studentCode() != null) {
            where.add("(ss.student_code = :code OR ts.student_code = :code)");
            params.put("code", filter.studentCode());
        }
        if (filter.before() != null) {
            where.add("t.id < :before");
            params.put("before", filter.before());
        }
        params.put("limit", filter.limit());
        String sql = SELECT + (where.isEmpty() ? "" : "WHERE " + String.join(" AND ", where) + "\n")
                + "ORDER BY t.id DESC LIMIT :limit";
        return jdbc.sql(sql).params(params).query(LedgerJdbcAdapter::map).list();
    }

    private static Entry map(ResultSet rs, int row) throws SQLException {
        return new Entry(rs.getLong("id"), TransactionType.valueOf(rs.getString("type")), rs.getLong("points"),
                rs.getObject("cash_amount", Long.class),
                party(rs, "src"), party(rs, "dst"),
                rs.getString("performer_name"), rs.getString("performer_role"),
                SqlTime.instant(rs, "created_at"), rs.getString("plate_number"), rs.getString("reason"),
                rs.getObject("original_transaction_id", Long.class));
    }

    /** Bên chuyển / nhận; null khi giao dịch không có ví ở phía đó (đổi điểm không có nguồn, rút điểm không có đích). */
    private static Party party(ResultSet rs, String prefix) throws SQLException {
        String name = rs.getString(prefix + "_name");
        return name == null ? null : new Party(name, rs.getString(prefix + "_code"), rs.getString(prefix + "_role"));
    }
}
