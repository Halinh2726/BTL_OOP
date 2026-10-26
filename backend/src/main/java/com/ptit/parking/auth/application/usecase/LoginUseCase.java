package com.ptit.parking.auth.application.usecase;

import com.ptit.parking.auth.application.AuthenticatedUser;
import com.ptit.parking.auth.application.port.out.PermissionRepository;
import com.ptit.parking.auth.application.port.out.UserRepository;
import com.ptit.parking.auth.domain.PasswordHasher;
import com.ptit.parking.auth.domain.User;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.util.Optional;

/**
 * C01 — Đăng nhập. Use case chỉ ĐIỀU PHỐI: tìm người dùng, nhờ {@link User} tự so mật khẩu
 * và tự kiểm tra trạng thái; quy tắc nghiệp vụ nằm trong domain, không nằm ở đây.
 * <p>Chỉ phụ thuộc interface (UserRepository, PermissionRepository, PasswordHasher) —
 * nhờ vậy test được bằng dữ liệu giả, không cần CSDL hay Spring.
 */
public class LoginUseCase {

    private final UserRepository users;
    private final PermissionRepository permissions;
    private final PasswordHasher hasher;
    private final String dummyHash;

    public LoginUseCase(UserRepository users, PermissionRepository permissions, PasswordHasher hasher) {
        this.users = users;
        this.permissions = permissions;
        this.hasher = hasher;
        // Băm sẵn một mật khẩu giả để so khi username KHÔNG tồn tại (xem login())
        this.dummyHash = hasher.hash("timing-equalizer");
    }

    public AuthenticatedUser login(String username, String rawPassword) {
        Optional<User> found = users.findByUsername(username.strip());
        if (found.isEmpty()) {
            // Vẫn chạy BCrypt một lần để thời gian phản hồi như khi sai mật khẩu:
            // kẻ tấn công không đo được "username này có tồn tại hay không" (Đặc tả API #1)
            hasher.matches(rawPassword, dummyHash);
            throw new DomainException(ErrorCode.INVALID_CREDENTIALS);
        }
        User user = found.get();
        if (!user.passwordMatches(rawPassword, hasher)) {
            throw new DomainException(ErrorCode.INVALID_CREDENTIALS);   // không nói rõ sai phần nào
        }
        user.ensureActive();                                            // BI_KHOA -> ACCOUNT_LOCKED
        return AuthenticatedUser.of(user, permissions.findByRole(user.role()));
    }
}
