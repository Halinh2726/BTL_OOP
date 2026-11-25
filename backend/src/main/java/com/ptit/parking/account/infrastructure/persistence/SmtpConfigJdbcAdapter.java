package com.ptit.parking.account.infrastructure.persistence;

import com.ptit.parking.account.application.port.out.SmtpConfigRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Cài đặt {@link SmtpConfigRepository} trên bảng smtp_config (tối đa 1 dòng id = 1). */
@Component
class SmtpConfigJdbcAdapter implements SmtpConfigRepository {

    private final JdbcClient jdbc;

    SmtpConfigJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SavedSmtpConfig> findSaved() {
        return jdbc.sql("""
                SELECT enabled, host, port, username, password, from_address, starttls, ssl_enabled, alert_email, updated_at
                FROM smtp_config
                WHERE id = 1""")
                .query((rs, row) -> {
                    SmtpSettings settings = new SmtpSettings(
                            rs.getBoolean("enabled"),
                            rs.getString("host"),
                            rs.getInt("port"),
                            rs.getString("username"),
                            rs.getString("password"),
                            rs.getString("from_address"),
                            rs.getBoolean("starttls"),
                            rs.getBoolean("ssl_enabled"),
                            rs.getString("alert_email"));
                    Instant updatedAt = rs.getObject("updated_at", LocalDateTime.class).toInstant(ZoneOffset.UTC);
                    return new SavedSmtpConfig(settings, updatedAt);
                })
                .optional();
    }

    @Override
    public SavedSmtpConfig save(SmtpSettings settings, long updatedByUserId) {
        jdbc.sql("""
                INSERT INTO smtp_config
                    (id, enabled, host, port, username, password, from_address, starttls, ssl_enabled, alert_email, updated_by)
                VALUES
                    (1, :enabled, :host, :port, :username, :password, :fromAddress, :starttls, :sslEnabled, :alertEmail, :updatedBy)
                ON DUPLICATE KEY UPDATE
                    enabled      = VALUES(enabled),
                    host         = VALUES(host),
                    port         = VALUES(port),
                    username     = VALUES(username),
                    password     = VALUES(password),
                    from_address = VALUES(from_address),
                    starttls     = VALUES(starttls),
                    ssl_enabled  = VALUES(ssl_enabled),
                    alert_email  = VALUES(alert_email),
                    updated_by   = VALUES(updated_by),
                    updated_at   = CURRENT_TIMESTAMP(6)""")
                .param("enabled", settings.enabled())
                .param("host", settings.host())
                .param("port", settings.port())
                .param("username", settings.username())
                .param("password", settings.password())
                .param("fromAddress", settings.fromAddress())
                .param("starttls", settings.starttls())
                .param("sslEnabled", settings.sslEnabled())
                .param("alertEmail", settings.alertEmail())
                .param("updatedBy", updatedByUserId)
                .update();
        return findSaved().orElseThrow();
    }

    @Override
    public void deleteSaved() {
        jdbc.sql("DELETE FROM smtp_config WHERE id = 1").update();
    }
}
