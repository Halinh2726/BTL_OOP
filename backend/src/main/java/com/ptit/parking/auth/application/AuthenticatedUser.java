package com.ptit.parking.auth.application;

import com.ptit.parking.auth.domain.Role;
import com.ptit.parking.auth.domain.User;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * Người dùng đã đăng nhập — thông tin được lưu trong PHIÊN (session) phía máy chủ.
 * Bất biến (record) và không chứa mật khẩu/hash. Serializable vì phiên có thể được lưu ra ngoài bộ nhớ.
 */
public record AuthenticatedUser(long id, String username, String fullName, Role role, List<String> permissions)
        implements Serializable {

    public AuthenticatedUser {
        permissions = List.copyOf(permissions);
    }

    public static AuthenticatedUser of(User user, Collection<String> permissions) {
        return new AuthenticatedUser(user.id(), user.username(), user.fullName(), user.role(),
                permissions.stream().sorted().toList());
    }
}
