package com.ptit.parking.owner.adapter.out.persistence;

import com.ptit.parking.owner.application.port.out.OwnerFinanceQuery;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Cài đặt {@link OwnerFinanceQuery}: các câu SELECT tổng hợp trên sổ giao dịch. CSDL lưu giờ UTC nên nhóm theo ngày
 * phải đổi sang giờ Việt Nam bằng CONVERT_TZ (dùng độ lệch "+07:00", không cần bảng múi giờ của MySQL).
 */
@Component
class OwnerFinanceJdbcAdapter implements OwnerFinanceQuery {

    private final JdbcClient jdbc;

    OwnerFinanceJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ReceivingAccount receivingAccount() {
        return jdbc.sql("""
                SELECT u.id AS user_id, u.full_name, w.id AS wallet_id
                FROM system_config c
                JOIN wallets w ON w.id = c.receiving_wallet_id
                JOIN users u ON u.id = w.user_id
                WHERE c.id = 1""")
                .query((rs, row) -> new ReceivingAccount(rs.getLong("user_id"), rs.getString("full_name"),
                        rs.getLong("wallet_id")))
                .single();
    }

    @Override
    public long cashHeldByManager() {
        return jdbc.sql("SELECT cash_held_by_manager FROM v_reconciliation").query(Long.class).single();
    }

    @Override
    public List<DailyRevenue> dailyRevenue(Instant from, Instant to, ZoneId zone) {
        String offset = zone.getRules().getOffset(from).getId();          // "+07:00"
        long ownerWallet = receivingAccount().walletId();
        return jdbc.sql("""
                SELECT DATE(CONVERT_TZ(t.created_at, '+00:00', :offset)) AS day,
                       SUM(t.type = 'THANH_TOAN') AS payments,
                       COALESCE(SUM(CASE WHEN t.type IN ('THANH_TOAN', 'MUA_VE') AND t.target_wallet_id = :owner THEN t.points END), 0) AS revenue,
                       COALESCE(SUM(CASE WHEN t.type = 'DIEU_CHINH' AND t.target_wallet_id = :owner THEN t.points
                                         WHEN t.type = 'DIEU_CHINH' AND t.source_wallet_id = :owner THEN -t.points END), 0) AS adjustments,
                       COALESCE(SUM(CASE WHEN t.type = 'DOI_DIEM' THEN c.cash_amount END), 0) AS cash_in,
                       COALESCE(SUM(CASE WHEN t.type = 'RUT_DIEM' THEN c.cash_amount END), 0) AS cash_out
                FROM point_transactions t
                LEFT JOIN cash_details c ON c.transaction_id = t.id
                WHERE t.created_at >= :from AND t.created_at < :to
                GROUP BY day
                ORDER BY day""")
                .param("offset", offset)
                .param("owner", ownerWallet)
                .param("from", LocalDateTime.ofInstant(from, ZoneOffset.UTC))
                .param("to", LocalDateTime.ofInstant(to, ZoneOffset.UTC))
                .query((rs, row) -> new DailyRevenue(rs.getObject("day", LocalDate.class), rs.getLong("payments"),
                        rs.getLong("revenue"), rs.getLong("adjustments"), rs.getLong("cash_in"), rs.getLong("cash_out")))
                .list();
    }

    @Override
    public Map<Long, String> namesOf(Collection<Long> userIds) {
        Map<Long, String> names = new HashMap<>();
        if (!userIds.isEmpty()) {
            jdbc.sql("SELECT id, full_name FROM users WHERE id IN (:ids)")
                    .param("ids", List.copyOf(userIds))
                    .query((rs, row) -> names.put(rs.getLong("id"), rs.getString("full_name")))
                    .list();
        }
        return names;
    }
}
