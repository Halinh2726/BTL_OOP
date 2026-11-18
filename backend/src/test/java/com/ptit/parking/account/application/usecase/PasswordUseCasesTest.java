package com.ptit.parking.account.application.usecase;

import static com.ptit.parking.account.AccountFakes.DIRECTLY;
import static com.ptit.parking.account.AccountFakes.HASHER;
import static com.ptit.parking.account.AccountFakes.credential;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.account.AccountFakes.AuditLog;
import com.ptit.parking.account.AccountFakes.Codes;
import com.ptit.parking.account.AccountFakes.Credentials;
import com.ptit.parking.account.AccountFakes.FixedSecrets;
import com.ptit.parking.account.AccountFakes.Outbox;
import com.ptit.parking.auth.domain.Role;
import com.ptit.parking.shared.application.port.out.AuditTrail.AuditAction;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Đợt 1 — đổi mật khẩu, quên mật khẩu (OTP qua email), mật khẩu tạm do quản lý cấp. */
class PasswordUseCasesTest {

    private static final Instant NOW = Instant.parse("2026-11-01T03:00:00Z");

    private final MutableClock clock = new MutableClock(NOW);
    private final Credentials credentials = new Credentials()
            .add(credential(7L, Role.SINH_VIEN, "MatKhauCu1"), "an@ptit.edu.vn")
            .add(credential(4L, Role.BAO_VE, "BaoVe@123"), null)
            .add(credential(1L, Role.QUAN_LY, "Quanly@123"), "quanly@ptit.edu.vn");
    private final Codes codes = new Codes();
    private final Outbox outbox = new Outbox();
    private final AuditLog audit = new AuditLog();

    private final ChangePasswordUseCase change = new ChangePasswordUseCase(credentials, HASHER, audit, DIRECTLY, clock);
    private final RequestPasswordResetUseCase request = new RequestPasswordResetUseCase(credentials, codes, HASHER,
            new FixedSecrets(), outbox, audit, DIRECTLY, clock);
    private final ConfirmPasswordResetUseCase confirm = new ConfirmPasswordResetUseCase(credentials, codes, HASHER,
            audit, DIRECTLY, clock);
    private final IssueTemporaryPasswordUseCase temporary = new IssueTemporaryPasswordUseCase(credentials, HASHER,
            new FixedSecrets(), audit, DIRECTLY, clock);

    @Test
    void doiMatKhau_traVeThoiDiemDoi_ghiNhatKy() {
        Instant changedAt = change.change(new ChangePasswordUseCase.Command(7L, "MatKhauCu1", "MatKhauMoi1"));

        assertThat(changedAt).isEqualTo(NOW);
        assertThat(audit.entries).extracting(e -> e.action()).containsExactly(AuditAction.DOI_MAT_KHAU);
    }

    @Test
    void quenMatKhau_emailDung_guiMa_nhapDungMa_doiDuocMatKhau() {
        request.request(" AN@ptit.edu.vn ");

        assertThat(outbox.sent).hasSize(1);
        assertThat(outbox.sent.get(0).body()).contains("123456");
        confirm.confirm(new ConfirmPasswordResetUseCase.Command("an@ptit.edu.vn", "123456", "MatKhauMoi1"));

        assertThat(HASHER.matches("MatKhauMoi1", credentials.byId.get(7L).passwordHash())).isTrue();
        assertThat(audit.entries).extracting(e -> e.action())
                .containsExactly(AuditAction.YEU_CAU_KHOI_PHUC_MAT_KHAU, AuditAction.KHOI_PHUC_MAT_KHAU);
    }

    @Test
    void emailLa_khongGuiGi_vanKetThucBinhThuong_khongTietLoEmail() {
        request.request("ai-do@gmail.com");

        assertThat(outbox.sent).isEmpty();
        assertThat(codes.all).isEmpty();
    }

    @Test
    void guiLaiTrongVong1Phut_khongTaoMaMoi_sau1PhutThiGui() {
        request.request("an@ptit.edu.vn");
        request.request("an@ptit.edu.vn");
        assertThat(outbox.sent).hasSize(1);

        clock.advanceSeconds(61);
        request.request("an@ptit.edu.vn");
        assertThat(outbox.sent).hasSize(2);
    }

    @Test
    void nhapSaiMa_soLanSaiDuocLuu_roiMoiBaoLoi() {
        request.request("an@ptit.edu.vn");

        assertThatThrownBy(() -> confirm.confirm(
                new ConfirmPasswordResetUseCase.Command("an@ptit.edu.vn", "000000", "MatKhauMoi1")))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RESET_CODE_INVALID));
        assertThat(codes.all.get(0).failedAttempts()).isEqualTo(1);
        assertThat(HASHER.matches("MatKhauCu1", credentials.byId.get(7L).passwordHash())).isTrue();
    }

    @Test
    void matKhauMoiQuaNgan_khongTonLuotThuMa() {
        request.request("an@ptit.edu.vn");

        assertThatThrownBy(() -> confirm.confirm(
                new ConfirmPasswordResetUseCase.Command("an@ptit.edu.vn", "000000", "ngan")))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.PASSWORD_POLICY));
        assertThat(codes.all.get(0).failedAttempts()).isZero();
    }

    @Test
    void quanLyCapMatKhauTam_choBaoVe_batBuocDoi_ghiNhatKyNguoiCap() {
        IssueTemporaryPasswordUseCase.TemporaryPassword issued = temporary.forGuard(1L, 4L);

        assertThat(issued.password()).isEqualTo("TamThoi2345");
        assertThat(credentials.byId.get(4L).mustChangePassword()).isTrue();
        assertThat(audit.entries.get(0).actorId()).isEqualTo(1L);
        assertThat(audit.entries.get(0).targetUserId()).isEqualTo(4L);
    }

    @Test
    void capMatKhauTam_idKhongPhaiBaoVe_bao404() {
        assertThatThrownBy(() -> temporary.forGuard(1L, 7L))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.GUARD_NOT_FOUND));
    }

    /** Đồng hồ giả tua được — kiểm tra quy tắc "1 phút mới gửi lại mã". */
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
