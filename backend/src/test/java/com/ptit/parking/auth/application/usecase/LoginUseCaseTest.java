package com.ptit.parking.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.auth.application.AuthenticatedUser;
import com.ptit.parking.auth.application.port.out.PermissionRepository;
import com.ptit.parking.auth.application.port.out.UserRepository;
import com.ptit.parking.auth.domain.AccountStatus;
import com.ptit.parking.auth.domain.Guard;
import com.ptit.parking.auth.domain.Manager;
import com.ptit.parking.auth.domain.PasswordHasher;
import com.ptit.parking.auth.domain.Role;
import com.ptit.parking.auth.domain.User;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Test use case bằng các lớp GIẢ cài đặt chính interface của port — lợi ích trực tiếp
 * của Dependency Inversion: không cần CSDL, không cần Spring, chạy trong vài mili giây.
 */
class LoginUseCaseTest {

    private final FakeHasher hasher = new FakeHasher();
    private final FakeUsers users = new FakeUsers();
    private final PermissionRepository permissions = role -> switch (role) {
        case QUAN_LY -> Set.of("GUARD_LOCK", "CASH_DEPOSIT");
        case BAO_VE -> Set.of("PARKING_CHECK_IN");
        default -> Set.of();
    };

    private final LoginUseCase login = new LoginUseCase(users, permissions, hasher);
    private final GetCurrentUserUseCase currentUser = new GetCurrentUserUseCase(users, permissions);

    @Test
    void dangNhapDung_traVeThongTinVaQuyenDaSapXep() {
        users.add(new Manager(1L, "quanly01", hasher.hash("Quanly@123"), "Phạm Văn Quản", AccountStatus.HOAT_DONG));

        AuthenticatedUser result = login.login("quanly01", "Quanly@123");

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.role()).isEqualTo(Role.QUAN_LY);
        assertThat(result.permissions()).containsExactly("CASH_DEPOSIT", "GUARD_LOCK");
    }

    @Test
    void saiMatKhau_baoInvalidCredentials() {
        users.add(new Manager(1L, "quanly01", hasher.hash("Quanly@123"), "Phạm Văn Quản", AccountStatus.HOAT_DONG));

        assertError(() -> login.login("quanly01", "sai-mat-khau"), ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void usernameKhongTonTai_cungBaoInvalidCredentials_vaVanChayBCrypt() {
        int before = hasher.matchCalls;

        assertError(() -> login.login("khongco", "abc"), ErrorCode.INVALID_CREDENTIALS);
        assertThat(hasher.matchCalls).isEqualTo(before + 1);   // thời gian phản hồi như sai mật khẩu
    }

    @Test
    void taiKhoanBiKhoa_dungMatKhau_baoAccountLocked() {
        users.add(new Guard(4L, "baove01", hasher.hash("Baove@123"), "Trần Văn B", AccountStatus.BI_KHOA, "0901234567"));

        assertError(() -> login.login("baove01", "Baove@123"), ErrorCode.ACCOUNT_LOCKED);
    }

    @Test
    void taiKhoanBiKhoa_saiMatKhau_khongTietLoLaDaBiKhoa() {
        users.add(new Guard(4L, "baove01", hasher.hash("Baove@123"), "Trần Văn B", AccountStatus.BI_KHOA, "0901234567"));

        assertError(() -> login.login("baove01", "doan-bua"), ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void me_taiKhoanVuaBiKhoa_baoAccountLocked() {
        Guard guard = new Guard(4L, "baove01", hasher.hash("x"), "Trần Văn B", AccountStatus.HOAT_DONG, "0901234567");
        users.add(guard);
        assertThat(currentUser.get(4L).username()).isEqualTo("baove01");

        guard.lock();                                            // quản lý khóa trong lúc bảo vệ đang đăng nhập

        assertError(() -> currentUser.get(4L), ErrorCode.ACCOUNT_LOCKED);
    }

    @Test
    void me_nguoiDungKhongConTonTai_baoUnauthenticated() {
        assertError(() -> currentUser.get(99L), ErrorCode.UNAUTHENTICATED);
    }

    private static void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(expected));
    }

    // ----- Lớp giả cài đặt interface của port -----

    /** "Băm" giả: đủ để test logic, không chậm như BCrypt thật. */
    static final class FakeHasher implements PasswordHasher {
        int matchCalls;

        @Override
        public String hash(String rawPassword) {
            return "hashed:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String passwordHash) {
            matchCalls++;
            return passwordHash.equals(hash(rawPassword));
        }
    }

    static final class FakeUsers implements UserRepository {
        private final Map<Long, User> byId = new HashMap<>();

        void add(User user) {
            byId.put(user.id(), user);
        }

        @Override
        public Optional<User> findByUsername(String username) {
            return byId.values().stream().filter(u -> u.username().equals(username)).findFirst();
        }

        @Override
        public Optional<User> findById(long id) {
            return Optional.ofNullable(byId.get(id));
        }
    }
}
