package com.ptit.parking.auth.adapter.out.persistence;

import com.ptit.parking.auth.domain.AccountStatus;
import com.ptit.parking.auth.domain.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Bản ghi bảng {@code users} cho JPA. TÁCH RIÊNG khỏi domain {@code User}:
 * domain không biết JPA, entity không chứa quy tắc nghiệp vụ. Mapper chuyển đổi giữa hai bên.
 */
@Entity
@Table(name = "users")
public class UserJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_code", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    protected UserJpaEntity() {
        // JPA cần constructor rỗng
    }

    public Long getId() { return id; }

    public String getUsername() { return username; }

    public String getPasswordHash() { return passwordHash; }

    public String getFullName() { return fullName; }

    public Role getRole() { return role; }

    public AccountStatus getStatus() { return status; }
}
