package com.ptit.parking.payment.infrastructure.persistence;

import com.ptit.parking.payment.application.port.out.BankConfigRepository;
import com.ptit.parking.payment.domain.BankAccountConfig;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Cài đặt {@link BankConfigRepository} trên bảng bank_config (tối đa 1 dòng id = 1). */
@Component
class BankConfigJdbcAdapter implements BankConfigRepository {

    private final JdbcClient jdbc;

    BankConfigJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SavedBankConfig> findSaved() {
        return jdbc.sql("""
                SELECT bank_id, account_no, account_name, template, updated_at
                FROM bank_config
                WHERE id = 1""")
                .query((rs, row) -> {
                    BankAccountConfig config = new BankAccountConfig(
                            rs.getString("bank_id"),
                            rs.getString("account_no"),
                            rs.getString("account_name"),
                            rs.getString("template"));
                    Instant updatedAt = rs.getObject("updated_at", LocalDateTime.class).toInstant(ZoneOffset.UTC);
                    return new SavedBankConfig(config, updatedAt);
                })
                .optional();
    }

    @Override
    public SavedBankConfig save(BankAccountConfig config, long updatedByUserId) {
        jdbc.sql("""
                INSERT INTO bank_config (id, bank_id, account_no, account_name, template, updated_by)
                VALUES (1, :bankId, :accountNo, :accountName, :template, :updatedBy)
                ON DUPLICATE KEY UPDATE
                    bank_id      = VALUES(bank_id),
                    account_no   = VALUES(account_no),
                    account_name = VALUES(account_name),
                    template     = VALUES(template),
                    updated_by   = VALUES(updated_by),
                    updated_at   = CURRENT_TIMESTAMP(6)""")
                .param("bankId", config.bankId())
                .param("accountNo", config.accountNo())
                .param("accountName", config.accountName())
                .param("template", config.template())
                .param("updatedBy", updatedByUserId)
                .update();
        return findSaved().orElseThrow();
    }

    @Override
    public void deleteSaved() {
        jdbc.sql("DELETE FROM bank_config WHERE id = 1").update();
    }
}
