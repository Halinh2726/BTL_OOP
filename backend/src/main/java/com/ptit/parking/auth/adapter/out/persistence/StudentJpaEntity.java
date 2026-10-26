package com.ptit.parking.auth.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Bản ghi bảng {@code students}: phần dữ liệu RIÊNG của lớp con Student (khóa chính = users.id). */
@Entity
@Table(name = "students")
public class StudentJpaEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "student_code", nullable = false, unique = true)
    private String studentCode;

    protected StudentJpaEntity() {
    }

    public Long getUserId() { return userId; }

    public String getStudentCode() { return studentCode; }
}
