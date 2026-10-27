package com.ptit.parking.student.adapter.out.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import com.ptit.parking.auth.domain.Student;
import com.ptit.parking.student.application.port.out.StudentAccountRepository;

/**
 * Cài đặt {@link StudentAccountRepository}: một sinh viên = một dòng users (phần chung của mọi vai trò)
 * + một dòng students (phần riêng: MSSV) — đúng kiểu ánh xạ kế thừa "Class Table Inheritance" của schema.
 */
@Component
class StudentAccountJdbcAdapter implements StudentAccountRepository {

    private final JdbcClient jdbc;

    StudentAccountJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean studentCodeExists(String studentCode) {
        return jdbc.sql("SELECT COUNT(*) FROM students WHERE student_code = :code")
                .param("code", studentCode)
                .query(Long.class)
                .single() > 0;
    }

    @Override
    public boolean usernameExists(String username) {
        return jdbc.sql("SELECT COUNT(*) FROM users WHERE username = :username")
                .param("username", username)
                .query(Long.class)
                .single() > 0;
    }

    @Override
    public Student save(Student student) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO users (username, password_hash, full_name, role_code, status)
                VALUES (:username, :passwordHash, :fullName, :role, :status)""")
                .param("username", student.username())
                .param("passwordHash", student.passwordHash())
                .param("fullName", student.fullName())
                .param("role", student.role().name())
                .param("status", student.status().name())
                .update(keys);
        long id = keys.getKey().longValue();

        jdbc.sql("INSERT INTO students (user_id, student_code) VALUES (:id, :code)")
                .param("id", id)
                .param("code", student.studentCode())
                .update();

        return new Student(id, student.username(), student.passwordHash(), student.fullName(),
                student.status(), student.studentCode());
    }
}