package com.ptit.parking.gate.adapter.out.persistence;

import com.ptit.parking.gate.application.port.out.GuardRoster;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Cài đặt {@link GuardRoster}: bảo vệ = users JOIN guards; hoạt động = status HOAT_DONG. */
@Component
class GuardRosterJdbcAdapter implements GuardRoster {

    private static final String SELECT = """
            SELECT u.id, u.full_name, u.status = 'HOAT_DONG' AS active
            FROM guards g JOIN users u ON u.id = g.user_id
            """;

    private final JdbcClient jdbc;

    GuardRosterJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<GuardInfo> all() {
        return jdbc.sql(SELECT + "ORDER BY u.full_name")
                .query((rs, row) -> new GuardInfo(rs.getLong("id"), rs.getString("full_name"), rs.getBoolean("active")))
                .list();
    }

    @Override
    public Optional<GuardInfo> find(long guardId) {
        return jdbc.sql(SELECT + "WHERE u.id = :id").param("id", guardId)
                .query((rs, row) -> new GuardInfo(rs.getLong("id"), rs.getString("full_name"), rs.getBoolean("active")))
                .optional();
    }
}
