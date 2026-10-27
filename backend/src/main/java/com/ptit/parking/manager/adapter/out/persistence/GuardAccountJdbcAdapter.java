package com.ptit.parking.manager.adapter.out.persistence;

import com.ptit.parking.auth.domain.AccountStatus;
import com.ptit.parking.auth.domain.Guard;
import com.ptit.parking.manager.application.port.out.GuardAccountRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

/**
 * Cài đặt {@link GuardAccountRepository}: một bảo vệ = một dòng users (phần chung) + một dòng guards
 * (phần riêng: số điện thoại) — cùng kiểu ánh xạ kế thừa như sinh viên.
 */
@Component
class GuardAccountJdbcAdapter implements GuardAccountRepository {

    /** JOIN guards: chỉ ra người có dòng trong guards — id của vai trò khác không bao giờ khớp. */
    private static final String SELECT_GUARD = """
            SELECT u.id, u.username, u.password_hash, u.full_name, u.status, g.phone
            FROM users u
            JOIN guards g ON g.user_id = u.id
            """;

    private final JdbcClient jdbc;

    GuardAccountJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean usernameExists(String username) {
        return jdbc.sql("SELECT COUNT(*) FROM users WHERE username = :username")
                .param("username", username)
                .query(Long.class)
                .single() > 0;
    }

    @Override
    public Guard save(Guard guard) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO users (username, password_hash, full_name, role_code, status)
                VALUES (:username, :passwordHash, :fullName, :role, :status)""")
                .param("username", guard.username())
                .param("passwordHash", guard.passwordHash())
                .param("fullName", guard.fullName())
                .param("role", guard.role().name())
                .param("status", guard.status().name())
                .update(keys);
        long id = keys.getKey().longValue();

        jdbc.sql("INSERT INTO guards (user_id, phone) VALUES (:id, :phone)")
                .param("id", id)
                .param("phone", guard.phone())
                .update();

        return new Guard(id, guard.username(), guard.passwordHash(), guard.fullName(), guard.status(), guard.phone());
    }

    @Override
    public List<Guard> findAll() {
        return jdbc.sql(SELECT_GUARD + "ORDER BY u.full_name, u.id")
                .query(GuardAccountJdbcAdapter::toGuard)
                .list();
    }

    @Override
    public Optional<Guard> findById(long userId) {
        return jdbc.sql(SELECT_GUARD + "WHERE u.id = :id")
                .param("id", userId)
                .query(GuardAccountJdbcAdapter::toGuard)
                .optional();
    }

    @Override
    public void saveStatus(Guard guard) {
        jdbc.sql("UPDATE users SET status = :status WHERE id = :id")
                .param("status", guard.status().name())
                .param("id", guard.id())
                .update();
    }

    private static Guard toGuard(ResultSet rs, int row) throws SQLException {
        return new Guard(rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"),
                rs.getString("full_name"), AccountStatus.valueOf(rs.getString("status")), rs.getString("phone"));
    }
}