package com.ptit.parking.auth.application;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import com.ptit.parking.auth.domain.Role;
import com.ptit.parking.auth.domain.User;
import com.ptit.parking.shared.security.SessionUser;

/**
 * Người dùng đã đăng nhập — thông tin được lưu trong PHIÊN (session) phía máy chủ.
 * Bất biến (record) và không chứa mật khẩu/hash. Serializable vì phiên có thể được lưu ra ngoài bộ nhớ.
 * Cài đặt {@link SessionUser} để tầng bảo mật dùng chung đọc được mã người dùng mà không phụ thuộc module auth.
 */
public record AuthenticatedUser(long id, String username, String fullName, Role role, List<String> permissions)
        implements SessionUser, Serializable {

    public AuthenticatedUser {
        permissions = List.copyOf(permissions);
    }

    public static AuthenticatedUser of(User user, Collection<String> permissions) {
        return new AuthenticatedUser(user.id(), user.username(), user.fullName(), user.role(),
                permissions.stream().sorted().toList());
    }
}
