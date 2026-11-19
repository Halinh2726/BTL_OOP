package com.ptit.parking.report.adapter.out.persistence;

import com.ptit.parking.report.application.port.out.CashPositionQuery;
import com.ptit.parking.report.domain.CashPosition;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Cài đặt {@link CashPositionQuery}: MỘT lần quét sổ giao dịch đến hết ngày — phần trước ngày cho tiền đầu ngày,
 * phần trong ngày cho tiền vào / ra, số giao dịch và doanh thu. R12 đọc từ view v_reconciliation có sẵn (V008).
 */
@Component
class CashPositionJdbcAdapter implements CashPositionQuery {

    private final JdbcClient jdbc;

    CashPositionJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CashPosition positionOn(LocalDate date, ZoneId zone) {
        Instant start = date.atStartOfDay(zone).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(zone).toInstant();
        boolean balanced = jdbc.sql("SELECT points_balanced FROM v_reconciliation").query(Long.class).single() == 1L;
        return jdbc.sql("""
                SELECT COALESCE(SUM(CASE WHEN t.type = 'DOI_DIEM' AND t.created_at <  :start THEN c.cash_amount END), 0)
                     - COALESCE(SUM(CASE WHEN t.type = 'RUT_DIEM' AND t.created_at <  :start THEN c.cash_amount END), 0) AS opening,
                       COALESCE(SUM(CASE WHEN t.type = 'DOI_DIEM' AND t.created_at >= :start THEN c.cash_amount END), 0) AS cash_in,
                       COALESCE(SUM(CASE WHEN t.type = 'RUT_DIEM' AND t.created_at >= :start THEN c.cash_amount END), 0) AS cash_out,
                       COALESCE(SUM(t.type = 'DOI_DIEM'   AND t.created_at >= :start), 0) AS deposits,
                       COALESCE(SUM(t.type = 'RUT_DIEM'   AND t.created_at >= :start), 0) AS withdrawals,
                       COALESCE(SUM(t.type = 'THANH_TOAN' AND t.created_at >= :start), 0) AS payments,
                       COALESCE(SUM(CASE WHEN t.type IN ('THANH_TOAN', 'MUA_VE') AND t.created_at >= :start THEN t.points END), 0) AS revenue
                FROM point_transactions t
                LEFT JOIN cash_details c ON c.transaction_id = t.id
                WHERE t.created_at < :end""")
                .param("start", SqlTime.utc(start))
                .param("end", SqlTime.utc(end))
                .query((rs, row) -> new CashPosition(date, rs.getLong("opening"), rs.getLong("cash_in"),
                        rs.getLong("cash_out"), rs.getLong("deposits"), rs.getLong("withdrawals"),
                        rs.getLong("payments"), rs.getLong("revenue"), balanced))
                .single();
    }
}
