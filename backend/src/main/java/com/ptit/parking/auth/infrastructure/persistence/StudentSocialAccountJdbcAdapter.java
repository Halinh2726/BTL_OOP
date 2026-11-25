package com.ptit.parking.auth.infrastructure.persistence;

import com.ptit.parking.auth.application.port.out.StudentSocialAccounts;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class StudentSocialAccountJdbcAdapter implements StudentSocialAccounts {

    private final JdbcClient jdbc;

    StudentSocialAccountJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Long> findStudent(String provider, String subject) {
        return jdbc.sql("SELECT user_id FROM student_social_accounts WHERE provider = :provider AND subject = :subject")
                .param("provider", provider)
                .param("subject", subject)
                .query(Long.class)
                .optional();
    }

    @Override
    public List<String> linkedProviders(long studentId) {
        return jdbc.sql("SELECT provider FROM student_social_accounts WHERE user_id = :id ORDER BY provider")
                .param("id", studentId)
                .query(String.class)
                .list();
    }

    @Override
    public void insert(long studentId, String provider, String subject) {
        jdbc.sql("INSERT INTO student_social_accounts (user_id, provider, subject) VALUES (:id, :provider, :subject)")
                .param("id", studentId)
                .param("provider", provider)
                .param("subject", subject)
                .update();
    }
}
