package com.ptit.parking.account.adapter.out.persistence;

import com.ptit.parking.account.application.port.out.PasswordResetCodeRepository;
import com.ptit.parking.account.domain.PasswordResetCode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Cài đặt {@link PasswordResetCodeRepository} bằng SQL thuần. Thời điểm lưu theo UTC. */
@Component
class PasswordResetCodeJdbcAdapter implements PasswordResetCodeRepository {

    private static final String SELECT_LATEST = """
            SELECT id, user_id, code_hash, created_at, expires_at, failed_attempts, used_at
            FROM password_reset_codes
            WHERE user_id = :userId
            ORDER BY id DESC
            LIMIT 1
            """;

    private final JdbcClient jdbc;

    PasswordResetCodeJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(PasswordResetCode code) {
        if (code.id() == null) {
            jdbc.sql("""
                    INSERT INTO password_reset_codes (user_id, code_hash, created_at, expires_at, failed_attempts)
                    VALUES (:userId, :hash, :createdAt, :expiresAt, :failed)""")
                    .param("userId", code.userId())
                    .param("hash", code.codeHash())
                    .param("createdAt", utc(code.createdAt()))
                    .param("expiresAt", utc(code.expiresAt()))
                    .param("failed", code.failedAttempts())
                    .update();
            return;
        }
        jdbc.sql("UPDATE password_reset_codes SET failed_attempts = :failed, used_at = :usedAt WHERE id = :id")
                .param("failed", code.failedAttempts())
                .param("usedAt", utc(code.usedAt()))
                .param("id", code.id())
                .update();
    }

    @Override
    public Optional<PasswordResetCode> findLatest(long userId) {
        return jdbc.sql(SELECT_LATEST).param("userId", userId).query(PasswordResetCodeJdbcAdapter::map).optional();
    }

    @Override
    public Optional<PasswordResetCode> lockLatest(long userId) {
        return jdbc.sql(SELECT_LATEST + "FOR UPDATE").param("userId", userId)
                .query(PasswordResetCodeJdbcAdapter::map).optional();
    }

    private static PasswordResetCode map(ResultSet rs, int row) throws SQLException {
        LocalDateTime usedAt = rs.getObject("used_at", LocalDateTime.class);
        return PasswordResetCode.restore(rs.getLong("id"), rs.getLong("user_id"), rs.getString("code_hash"),
                rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                rs.getObject("expires_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                rs.getInt("failed_attempts"),
                usedAt == null ? null : usedAt.toInstant(ZoneOffset.UTC));
    }

    private static LocalDateTime utc(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
