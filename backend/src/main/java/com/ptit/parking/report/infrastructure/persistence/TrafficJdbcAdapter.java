package com.ptit.parking.report.infrastructure.persistence;

import com.ptit.parking.report.application.port.out.TrafficQuery;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Cài đặt {@link TrafficQuery}: GROUP BY giờ địa phương của entered_at / exited_at (CONVERT_TZ từ UTC). */
@Component
class TrafficJdbcAdapter implements TrafficQuery {

    private final JdbcClient jdbc;

    TrafficJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<Integer, Long> entriesByHour(Instant from, Instant to, ZoneId zone) {
        return byHour("entered_at", from, to, zone);
    }

    @Override
    public Map<Integer, Long> exitsByHour(Instant from, Instant to, ZoneId zone) {
        return byHour("exited_at", from, to, zone);
    }

    @Override
    public long parkedNow() {
        return jdbc.sql("SELECT COUNT(*) FROM parking_sessions WHERE status = 'DANG_GUI'").query(Long.class).single();
    }

    /** {@code column} là hằng trong lớp này (entered_at / exited_at), không đến từ người dùng. */
    private Map<Integer, Long> byHour(String column, Instant from, Instant to, ZoneId zone) {
        Map<Integer, Long> counts = new HashMap<>();
        jdbc.sql("""
                SELECT HOUR(CONVERT_TZ(%1$s, '+00:00', :offset)) AS hour_of_day, COUNT(*) AS total
                FROM parking_sessions
                WHERE %1$s >= :from AND %1$s < :to
                GROUP BY hour_of_day""".formatted(column))
                .param("offset", SqlTime.offset(zone, from))
                .param("from", SqlTime.utc(from))
                .param("to", SqlTime.utc(to))
                .query((rs, row) -> counts.put(rs.getInt("hour_of_day"), rs.getLong("total")))
                .list();
        return counts;
    }
}
