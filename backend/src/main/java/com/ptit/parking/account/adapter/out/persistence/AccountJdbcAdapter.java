package com.ptit.parking.account.adapter.out.persistence;

import com.ptit.parking.account.application.port.out.AccountProfileRepository;
import com.ptit.parking.account.application.port.out.CredentialRepository;
import com.ptit.parking.account.domain.AccountCredential;
import com.ptit.parking.account.domain.AccountProfile;
import com.ptit.parking.account.domain.EmailAddress;
import com.ptit.parking.account.domain.GuardProfile;
import com.ptit.parking.account.domain.PhoneNumber;
import com.ptit.parking.account.domain.StaffProfile;
import com.ptit.parking.account.domain.StudentProfile;
import com.ptit.parking.auth.domain.AccountStatus;
import com.ptit.parking.auth.domain.Role;
import com.ptit.parking.shared.security.CredentialStateLookup;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Cài đặt 3 port bằng SQL thuần: {@link AccountProfileRepository} (users + students/guards),
 * {@link CredentialRepository} và {@link CredentialStateLookup} (các cột mật khẩu của users).
 */
@Component
class AccountJdbcAdapter implements AccountProfileRepository, CredentialRepository, CredentialStateLookup {

    private static final String SELECT_PROFILE = """
            SELECT u.id, u.username, u.full_name, u.email, u.role_code,
                   s.student_code, s.phone AS student_phone, g.phone AS guard_phone
            FROM users u
            LEFT JOIN students s ON s.user_id = u.id
            LEFT JOIN guards g ON g.user_id = u.id
            """;

    private static final String SELECT_CREDENTIAL = """
            SELECT u.id, u.username, u.full_name, u.role_code, u.status, u.password_hash,
                   u.must_change_password, u.password_changed_at
            FROM users u
            """;

    private final JdbcClient jdbc;

    AccountJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ===== AccountProfileRepository =====

    @Override
    public Optional<AccountProfile> findById(long userId) {
        return jdbc.sql(SELECT_PROFILE + "WHERE u.id = :id")
                .param("id", userId)
                .query(AccountJdbcAdapter::toProfile)
                .optional();
    }

    @Override
    public boolean emailUsedByOther(EmailAddress email, long userId) {
        return jdbc.sql("SELECT COUNT(*) FROM users WHERE email = :email AND id <> :id")
                .param("email", email.value())
                .param("id", userId)
                .query(Long.class)
                .single() > 0;
    }

    /** Phần chung ở users; số điện thoại ở bảng con của đúng vai trò — switch trên lớp sealed, đủ 3 trường hợp. */
    @Override
    public void save(AccountProfile profile) {
        jdbc.sql("UPDATE users SET full_name = :fullName, email = :email WHERE id = :id")
                .param("fullName", profile.fullName())
                .param("email", profile.email().map(EmailAddress::value).orElse(null))
                .param("id", profile.userId())
                .update();
        switch (profile) {
            case StudentProfile student -> jdbc.sql("UPDATE students SET phone = :phone WHERE user_id = :id")
                    .param("phone", student.phone().map(PhoneNumber::value).orElse(null))
                    .param("id", student.userId())
                    .update();
            case GuardProfile guard -> jdbc.sql("UPDATE guards SET phone = :phone WHERE user_id = :id")
                    .param("phone", guard.phone().orElseThrow().value())
                    .param("id", guard.userId())
                    .update();
            case StaffProfile staff -> { /* không có bảng con */ }
        }
    }

    // ===== CredentialRepository =====

    @Override
    public Optional<AccountCredential> findByUserId(long userId) {
        return credential("WHERE u.id = :value", userId);
    }

    @Override
    public Optional<AccountCredential> findByEmail(EmailAddress email) {
        return credential("WHERE u.email = :value", email.value());
    }

    @Override
    public Optional<AccountCredential> findByUsername(String username) {
        return credential("WHERE u.username = :value", username);
    }

    @Override
    public Optional<AccountCredential> findByStudentCode(String studentCode) {
        return credential("JOIN students s ON s.user_id = u.id WHERE s.student_code = :value", studentCode);
    }

    @Override
    public Optional<EmailAddress> findEmailByUserId(long userId) {
        return jdbc.sql("SELECT email FROM users WHERE id = :id AND email IS NOT NULL")
                .param("id", userId)
                .query(String.class)
                .optional()
                .filter(raw -> !raw.isBlank())
                .map(EmailAddress::new);
    }

    @Override
    public void save(AccountCredential credential) {
        jdbc.sql("""
                UPDATE users SET password_hash = :hash, must_change_password = :mustChange,
                                 password_changed_at = :changedAt
                WHERE id = :id""")
                .param("hash", credential.passwordHash())
                .param("mustChange", credential.mustChangePassword())
                .param("changedAt", toUtc(credential.passwordChangedAt()))
                .param("id", credential.userId())
                .update();
    }

    @Override
    public int resetAllPasswords(String passwordHash, Instant changedAt) {
        return jdbc.sql("""
                UPDATE users SET password_hash = :hash, must_change_password = FALSE, password_changed_at = :changedAt""")
                .param("hash", passwordHash)
                .param("changedAt", toUtc(changedAt))
                .update();
    }

    // ===== CredentialStateLookup (chạy ở MỌI request đã đăng nhập: chỉ đọc 2 cột) =====

    @Override
    public CredentialState stateOf(long userId) {
        return jdbc.sql("SELECT password_changed_at, must_change_password FROM users WHERE id = :id")
                .param("id", userId)
                .query((rs, row) -> new CredentialState(instant(rs, "password_changed_at"),
                        rs.getBoolean("must_change_password")))
                .optional()
                .orElse(CredentialState.UNCHANGED);
    }

    // ===== Ánh xạ =====

    private Optional<AccountCredential> credential(String where, Object value) {
        return jdbc.sql(SELECT_CREDENTIAL + where)
                .param("value", value)
                .query((rs, row) -> new AccountCredential(rs.getLong("id"), rs.getString("username"),
                        rs.getString("full_name"), Role.valueOf(rs.getString("role_code")),
                        AccountStatus.valueOf(rs.getString("status")), rs.getString("password_hash"),
                        rs.getBoolean("must_change_password"), instant(rs, "password_changed_at")))
                .optional();
    }

    /** Nơi DUY NHẤT chọn lớp con hồ sơ theo role_code. */
    private static AccountProfile toProfile(ResultSet rs, int row) throws SQLException {
        long id = rs.getLong("id");
        String username = rs.getString("username");
        String fullName = rs.getString("full_name");
        EmailAddress email = rs.getString("email") == null ? null : new EmailAddress(rs.getString("email"));
        Role role = Role.valueOf(rs.getString("role_code"));
        return switch (role) {
            case SINH_VIEN -> new StudentProfile(id, username, fullName, email, rs.getString("student_code"),
                    PhoneNumber.ofNullable(rs.getString("student_phone")).orElse(null));
            case BAO_VE -> new GuardProfile(id, username, fullName, email, new PhoneNumber(rs.getString("guard_phone")));
            case QUAN_LY, CHU_TAI_KHOAN -> new StaffProfile(id, username, fullName, email, role);
        };
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static LocalDateTime toUtc(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
