package com.ptit.parking.auth.application.usecase;

import com.ptit.parking.auth.application.AuthenticatedUser;
import com.ptit.parking.auth.application.port.out.PermissionRepository;
import com.ptit.parking.auth.application.port.out.UserRepository;
import com.ptit.parking.auth.domain.User;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;

/**
 * C01 — GET /me: đọc lại người dùng từ CSDL (không tin dữ liệu cũ trong phiên),
 * để tài khoản vừa bị khóa nhận ngay ACCOUNT_LOCKED.
 */
public class GetCurrentUserUseCase {

    private final UserRepository users;
    private final PermissionRepository permissions;

    public GetCurrentUserUseCase(UserRepository users, PermissionRepository permissions) {
        this.users = users;
        this.permissions = permissions;
    }

    public AuthenticatedUser get(long userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new DomainException(ErrorCode.UNAUTHENTICATED));
        user.ensureActive();
        return AuthenticatedUser.of(user, permissions.findByRole(user.role()));
    }
}
