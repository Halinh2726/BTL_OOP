package com.ptit.parking.auth.application.port.out;

import com.ptit.parking.auth.domain.Role;
import java.util.Set;

/**
 * Port đọc ma trận quyền (bảng role_permissions). Tách riêng khỏi {@link UserRepository}
 * theo nguyên lý I (Interface Segregation): nơi nào chỉ cần quyền thì không phải phụ thuộc cả kho người dùng.
 */
public interface PermissionRepository {

    Set<String> findByRole(Role role);
}
