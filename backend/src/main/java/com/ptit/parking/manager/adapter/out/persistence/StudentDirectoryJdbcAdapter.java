package com.ptit.parking.manager.adapter.out.persistence;

import com.ptit.parking.manager.application.port.out.StudentDirectory;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Cài đặt {@link StudentDirectory}: ghép users + students + wallets trong một câu SQL. */
@Component
class StudentDirectoryJdbcAdapter implements StudentDirectory {

    private final JdbcClient jdbc;

    StudentDirectoryJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<StudentAccount> findByStudentCode(String studentCode) {
        return jdbc.sql("""
                SELECT u.id, s.student_code, u.full_name, w.id AS wallet_id
                FROM students s
                JOIN users u ON u.id = s.user_id
                JOIN wallets w ON w.user_id = u.id
                WHERE s.student_code = :code""")
                .param("code", studentCode)
                .query((rs, row) -> new StudentAccount(rs.getLong("id"), rs.getString("student_code"),
                        rs.getString("full_name"), rs.getLong("wallet_id")))
                .optional();
    }
}