package com.ptit.parking.report.infrastructure.persistence;

import com.ptit.parking.report.application.port.out.ReconciliationRepository;
import com.ptit.parking.report.domain.DailyReconciliation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

/** Cài đặt {@link ReconciliationRepository} trên bảng daily_reconciliations (chỉ INSERT / SELECT). */
@Component
class ReconciliationJdbcAdapter implements ReconciliationRepository {

    private static final String SELECT = """
            SELECT r.id, r.business_date, r.cash_in, r.cash_out, r.expected_cash, r.counted_cash, r.points_balanced,
                   r.note, r.closed_by, r.closed_at, u.full_name AS closed_by_name
            FROM daily_reconciliations r
            JOIN users u ON u.id = r.closed_by
            """;

    private final JdbcClient jdbc;

    ReconciliationJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public DailyReconciliation save(DailyReconciliation r) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO daily_reconciliations (business_date, cash_in, cash_out, expected_cash, counted_cash,
                                                   points_balanced, note, closed_by, closed_at)
                VALUES (:date, :cashIn, :cashOut, :expected, :counted, :balanced, :note, :closedBy, :closedAt)""")
                .param("date", r.businessDate())
                .param("cashIn", r.cashIn())
                .param("cashOut", r.cashOut())
                .param("expected", r.expectedCash())
                .param("counted", r.countedCash())
                .param("balanced", r.pointsBalanced())
                .param("note", r.note())
                .param("closedBy", r.closedBy())
                .param("closedAt", SqlTime.utc(r.closedAt()))
                .update(keys);
        return r.withId(keys.getKeyAs(Number.class).longValue());
    }

    @Override
    public Optional<Closed> findByDate(LocalDate date) {
        return jdbc.sql(SELECT + "WHERE r.business_date = :date").param("date", date)
                .query(ReconciliationJdbcAdapter::map).optional();
    }

    @Override
    public List<Closed> recent(int limit) {
        return jdbc.sql(SELECT + "ORDER BY r.business_date DESC LIMIT :limit").param("limit", limit)
                .query(ReconciliationJdbcAdapter::map).list();
    }

    private static Closed map(ResultSet rs, int row) throws SQLException {
        DailyReconciliation r = DailyReconciliation.restore(rs.getLong("id"),
                rs.getObject("business_date", LocalDate.class), rs.getLong("cash_in"), rs.getLong("cash_out"),
                rs.getLong("expected_cash"), rs.getLong("counted_cash"), rs.getBoolean("points_balanced"),
                rs.getString("note"), rs.getLong("closed_by"), SqlTime.instant(rs, "closed_at"));
        return new Closed(r, rs.getString("closed_by_name"));
    }
}
