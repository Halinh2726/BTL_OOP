package com.ptit.parking.account.domain;

import static com.ptit.parking.account.AccountFakes.HASHER;
import static com.ptit.parking.account.AccountFakes.credential;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.auth.domain.Role;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Đợt 1 — quy tắc mật khẩu, mã OTP và hồ sơ cá nhân nằm TRONG đối tượng domain. */
class AccountDomainTest {

    private static final Instant NOW = Instant.parse("2026-11-01T03:00:00.123456789Z");

    private static void assertCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(expected));
    }

    @Nested
    class MatKhau {

        @Test
        void doiMatKhau_dungMatKhauCu_ghiThoiDiemDoi_boCoTam() {
            AccountCredential c = credential(7L, Role.SINH_VIEN, "MatKhauCu1");

            c.change("MatKhauCu1", "MatKhauMoi1", HASHER, NOW);

            assertThat(HASHER.matches("MatKhauMoi1", c.passwordHash())).isTrue();
            assertThat(c.passwordChangedAt()).isEqualTo(Instant.parse("2026-11-01T03:00:00.123456Z"));
            assertThat(c.mustChangePassword()).isFalse();
        }

        @Test
        void saiMatKhauCu_trungMatKhauCu_quaNgan_deuBiTuChoi() {
            AccountCredential c = credential(7L, Role.SINH_VIEN, "MatKhauCu1");

            assertCode(() -> c.change("sai", "MatKhauMoi1", HASHER, NOW), ErrorCode.CURRENT_PASSWORD_INCORRECT);
            assertCode(() -> c.change("MatKhauCu1", "MatKhauCu1", HASHER, NOW), ErrorCode.PASSWORD_REUSED);
            assertCode(() -> c.change("MatKhauCu1", "ngan", HASHER, NOW), ErrorCode.PASSWORD_POLICY);
            assertThat(c.passwordChangedAt()).isNull();
        }

        @Test
        void matKhauTam_batBuocDoi_chiChoSinhVienVaBaoVe() {
            AccountCredential guard = credential(4L, Role.BAO_VE, "BaoVe@123");
            guard.issueTemporary("TamThoi2345", HASHER, NOW);
            assertThat(guard.mustChangePassword()).isTrue();

            AccountCredential owner = credential(2L, Role.CHU_TAI_KHOAN, "ChuBai@123");
            assertCode(() -> owner.issueTemporary("TamThoi2345", HASHER, NOW), ErrorCode.FORBIDDEN);
        }
    }

    @Nested
    class MaKhoiPhuc {

        @Test
        void dungMa_trongHan_dungMotLan() {
            PasswordResetCode code = PasswordResetCode.issue(7L, "123456", HASHER, NOW);

            assertThat(code.verify("123456", HASHER, NOW.plusSeconds(60))).isTrue();
            assertCode(() -> code.verify("123456", HASHER, NOW.plusSeconds(61)), ErrorCode.RESET_CODE_EXPIRED);
        }

        @Test
        void quaMuoiPhut_hetHan() {
            PasswordResetCode code = PasswordResetCode.issue(7L, "123456", HASHER, NOW);

            assertCode(() -> code.verify("123456", HASHER, NOW.plus(PasswordResetCode.TTL)), ErrorCode.RESET_CODE_EXPIRED);
        }

        @Test
        void sai5Lan_maBiKhoa_duNhapDungCungKhongDuoc() {
            PasswordResetCode code = PasswordResetCode.issue(7L, "123456", HASHER, NOW);
            for (int i = 0; i < PasswordResetCode.MAX_FAILED_ATTEMPTS; i++) {
                assertThat(code.verify("000000", HASHER, NOW)).isFalse();
            }

            assertThat(code.failedAttempts()).isEqualTo(5);
            assertCode(() -> code.verify("123456", HASHER, NOW), ErrorCode.RESET_CODE_LOCKED);
        }
    }

    @Nested
    class HoSo {

        @Test
        void sinhVien_doiHoTenEmailSdt_traVeTruongDaDoi_khongDoiMssv() {
            StudentProfile p = new StudentProfile(7L, "an.nv", "Nguyễn Văn An", null, "B21DCCN001", null);

            var changed = p.update("  Nguyễn   Văn  An  ", EmailAddress.of("An@PTIT.edu.vn "), PhoneNumber.of("091 234 5678"));

            assertThat(changed).containsExactly("email", "số điện thoại");    // họ tên chỉ khác khoảng trắng
            assertThat(p.email()).contains(new EmailAddress("an@ptit.edu.vn"));
            assertThat(p.phone()).contains(new PhoneNumber("0912345678"));
            assertThat(p.studentCode()).contains("B21DCCN001");
        }

        @Test
        void baoVe_batBuocSoDienThoai_quanLyKhongLuuSoDienThoai() {
            GuardProfile guard = new GuardProfile(4L, "baove01", "Trần Văn B", null, PhoneNumber.of("0912345678"));
            assertCode(() -> guard.update("Trần Văn B", null, null), ErrorCode.VALIDATION_ERROR);

            StaffProfile manager = new StaffProfile(1L, "quanly01", "Phạm Văn Quản", null, Role.QUAN_LY);
            assertThat(manager.update("Phạm Văn Quản", null, PhoneNumber.of("0912345678"))).isEmpty();
            assertThat(manager.phone()).isEmpty();
        }

        @Test
        void hoTenRong_emailSai_biTuChoi() {
            StudentProfile p = new StudentProfile(7L, "an.nv", "Nguyễn Văn An", null, "B21DCCN001", null);

            assertCode(() -> p.update("   ", null, null), ErrorCode.VALIDATION_ERROR);
            assertCode(() -> EmailAddress.of("khong-phai-email"), ErrorCode.VALIDATION_ERROR);
            assertThat(EmailAddress.of("an@ptit.edu.vn").masked()).isEqualTo("a***@ptit.edu.vn");
        }
    }
}
