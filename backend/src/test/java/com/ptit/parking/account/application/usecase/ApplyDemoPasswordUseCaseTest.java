package com.ptit.parking.account.application.usecase;

import static com.ptit.parking.account.AccountFakes.DIRECTLY;
import static com.ptit.parking.account.AccountFakes.HASHER;
import static com.ptit.parking.account.AccountFakes.credential;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.account.AccountFakes.Credentials;
import com.ptit.parking.auth.domain.Role;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Mật khẩu demo: một hash cho mọi tài khoản, vẫn phải đúng quy tắc mật khẩu. */
class ApplyDemoPasswordUseCaseTest {

    private final Credentials credentials = new Credentials()
            .add(credential(1L, Role.QUAN_LY, "Quanly@123"), null)
            .add(credential(4L, Role.BAO_VE, "Baove@123"), null);
    private final ApplyDemoPasswordUseCase demo = new ApplyDemoPasswordUseCase(credentials, HASHER, DIRECTLY,
            Clock.fixed(Instant.parse("2026-11-20T01:00:00Z"), ZoneOffset.UTC));

    @Test
    void datMatKhauDemoChoMoiTaiKhoan() {
        assertThat(demo.apply("demo1234")).isEqualTo(2);
        assertThat(HASHER.matches("demo1234", credentials.demoHash)).isTrue();
    }

    @Test
    void matKhauDemoQuaNgan_biTuChoi() {
        assertThatThrownBy(() -> demo.apply("demo"))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.PASSWORD_POLICY));
    }
}
