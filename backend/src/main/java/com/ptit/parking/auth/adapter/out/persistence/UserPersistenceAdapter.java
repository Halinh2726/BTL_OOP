package com.ptit.parking.auth.adapter.out.persistence;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ptit.parking.auth.application.port.out.PermissionRepository;
import com.ptit.parking.auth.application.port.out.UserRepository;
import com.ptit.parking.auth.domain.AccountStatus;
import com.ptit.parking.auth.domain.Guard;
import com.ptit.parking.auth.domain.Manager;
import com.ptit.parking.auth.domain.Owner;
import com.ptit.parking.auth.domain.Role;
import com.ptit.parking.auth.domain.Student;
import com.ptit.parking.auth.domain.User;
import com.ptit.parking.shared.security.AccountStatusLookup;
/**
 * Adapter cài đặt 3 port {@link UserRepository}, {@link PermissionRepository} và
 * {@link AccountStatusLookup} bằng JPA + MySQL.
 */
@Component
@Transactional(readOnly = true)
class UserPersistenceAdapter implements UserRepository, PermissionRepository, AccountStatusLookup {

    private final UserJpaRepository users;
    private final StudentJpaRepository students;
    private final GuardJpaRepository guards;

    UserPersistenceAdapter(UserJpaRepository users, StudentJpaRepository students, GuardJpaRepository guards) {
        this.users = users;
        this.students = students;
        this.guards = guards;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return users.findByUsername(username).map(this::toDomain);
    }

    @Override
    public Optional<User> findById(long id) {
        return users.findById(id).map(this::toDomain);
    }

    @Override
    public Set<String> findByRole(Role role) {
        return new HashSet<>(users.findPermissionCodesByRole(role.name()));
    }

    
    @Override
    public AccountState stateOf(long userId) {
        return users.findStatusById(userId)
                .map(status -> status == AccountStatus.BI_KHOA ? AccountState.LOCKED : AccountState.ACTIVE)
                .orElse(AccountState.NOT_FOUND);
    }
    /**
     * Nơi DUY NHẤT trong hệ thống chọn lớp con theo role_code: dữ liệu từ CSDL chỉ là dòng chữ,
     * phải dựng lại đúng kiểu đối tượng. Từ đây trở lên mọi tầng làm việc với kiểu {@link User}.
     * Switch trên enum được trình biên dịch kiểm tra đủ 4 vai trò.
     */
    private User toDomain(UserJpaEntity e) {
        return switch (e.getRole()) {
            case SINH_VIEN -> new Student(e.getId(), e.getUsername(), e.getPasswordHash(), e.getFullName(),
                    e.getStatus(), students.findById(e.getId()).orElseThrow().getStudentCode());
            case BAO_VE -> new Guard(e.getId(), e.getUsername(), e.getPasswordHash(), e.getFullName(),
                    e.getStatus(), guards.findById(e.getId()).orElseThrow().getPhone());
            case QUAN_LY -> new Manager(e.getId(), e.getUsername(), e.getPasswordHash(), e.getFullName(), e.getStatus());
            case CHU_TAI_KHOAN -> new Owner(e.getId(), e.getUsername(), e.getPasswordHash(), e.getFullName(), e.getStatus());
        };
    }
}
