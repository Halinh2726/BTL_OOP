package com.ptit.parking.auth.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Bản ghi bảng {@code guards}: phần dữ liệu RIÊNG của lớp con Guard (khóa chính = users.id). */
@Entity
@Table(name = "guards")
public class GuardJpaEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private String phone;

    protected GuardJpaEntity() {
    }

    public Long getUserId() { return userId; }

    public String getPhone() { return phone; }
}
