package com.ptit.parking.manager.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.auth.domain.AccountStatus;
import com.ptit.parking.auth.domain.Guard;
import com.ptit.parking.auth.domain.PasswordHasher;
import com.ptit.parking.auth.domain.Role;
import com.ptit.parking.manager.application.usecase.CreateGuardUseCase.Command;
import com.ptit.parking.shared.application.port.out.AuditTrail.AuditAction;
import com.ptit.parking.shared.application.port.out.AuditTrail.AuditEntry;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.manager.application.port.out.GuardAccountRepository;
import com.ptit.parking.manager.application.port.out.InMemoryGuardAccountRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** QL03 — tạo tài khoản bảo vệ. Không cần CSDL, không cần Spring. */
class CreateGuardUseCaseTest {

    private final InMemoryGuardAccountRepository guards = new InMemoryGuardAccountRepository();
    private final PasswordHasher hasher = new PasswordHasher() {
        @Override
        public String hash(String rawPassword) { return "hashed:" + rawPassword; }

        @Override
        public boolean matches(String rawPassword, String passwordHash) { return passwordHash.equals(hash(rawPassword)); }
    };
    private final TransactionRunner directly = new TransactionRunner() {
        @Override
        public <T> T inTransaction(Supplier<T> work) {
            return work.get();
        }
    };

    private final List<AuditEntry> logged = new ArrayList<>();
    private final CreateGuardUseCase createGuard = new CreateGuardUseCase(guards, hasher, directly, logged::add,
            Clock.fixed(Instant.parse("2026-11-20T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));

    @Test
    void taoBaoVe_ghiNhatKy_aiTao_taoChoAi() {
        Guard guard = createGuard.create(new Command(1L, "Ngô Văn H", "0912345678", "baove09", "MatKhau@123"));

        assertThat(logged).singleElement().satisfies(entry -> {
            assertThat(entry.actorId()).isEqualTo(1L);
            assertThat(entry.action()).isEqualTo(AuditAction.TAO_BAO_VE);
            assertThat(entry.targetUserId()).isEqualTo(guard.id());
            assertThat(entry.detail()).contains("baove09");
        });
    }

    @Test
    void trungTenDangNhap_khongGhiNhatKy() {
        createGuard.create(new Command(1L, "Bảo vệ cũ", "0900000000", "baove10", "MatKhau@123"));
        logged.clear();

        assertThatThrownBy(() -> createGuard.create(new Command(1L, "Ngô Văn H", "0912345678", "baove10", "MatKhau@123")))
                .isInstanceOf(DomainException.class);
        assertThat(logged).isEmpty();
    }

    @Test
    void taoThanhCong_vaiTroBaoVe_dangHoatDong_matKhauDaBam() {
        Guard guard = createGuard.create(new Command(1L, "  Ngô Văn H ", "0912345678", "baove03", "MatKhau@123"));

        assertThat(guard.id()).isEqualTo(1L);
        assertThat(guard.role()).isEqualTo(Role.BAO_VE);
        assertThat(guard.status()).isEqualTo(AccountStatus.HOAT_DONG);
        assertThat(guard.fullName()).isEqualTo("Ngô Văn H");
        assertThat(guard.phone()).isEqualTo("0912345678");
        assertThat(guard.passwordHash()).isEqualTo("hashed:MatKhau@123");      // không lưu mật khẩu rõ
    }

    @Test
    void trungTenDangNhap_baoUsernameExists_khongTaoGi() {
        createGuard.create(new Command(1L, "Bảo vệ cũ", "0900000000", "baove03", "MatKhau@123"));

        assertThatThrownBy(() -> createGuard.create(new Command(1L, "Ngô Văn H", "0912345678", "baove03", "MatKhau@123")))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.USERNAME_EXISTS));
        assertThat(guards.all()).hasSize(1);
    }
}