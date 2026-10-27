package com.ptit.parking.manager.adapter.out.persistence;

import com.ptit.parking.manager.application.port.out.OwnerAccountDirectory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Cài đặt {@link OwnerAccountDirectory}: system_config (1 dòng) → ví nhận → chủ ví. */
@Component
class OwnerAccountJdbcAdapter implements OwnerAccountDirectory {

    private final JdbcClient jdbc;

    OwnerAccountJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public OwnerAccount ownerAccount() {
        return jdbc.sql("""
                SELECT u.id AS user_id, u.full_name, w.id AS wallet_id
                FROM system_config c
                JOIN wallets w ON w.id = c.receiving_wallet_id
                JOIN users u ON u.id = w.user_id
                WHERE c.id = 1""")
                .query((rs, row) -> new OwnerAccount(rs.getLong("user_id"), rs.getString("full_name"),
                        rs.getLong("wallet_id")))
                .single();
    }
}
