package com.ptit.parking.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit test thuần Java: không khởi động Spring, không cần CSDL. */
class UserTest {

    @Test
    void moiLopConTuTraVeVaiTroCuaMinh() {
        // Biến kiểu cha User, đối tượng thật là 4 lớp con khác nhau
        List<User> users = List.of(
                Student.register("sv01", "hash", "Nguyễn Văn A", "b21dccn001"),
                Guard.create("baove01", "hash", "Trần Văn B", "0901234567"),
                new Manager(1L, "quanly01", "hash", "Phạm Văn Quản", AccountStatus.HOAT_DONG),
                new Owner(2L, "chutk01", "hash", "Lê Văn C", AccountStatus.HOAT_DONG));

        // Cùng gọi role(): JVM chạy đúng bản override của từng lớp con
        assertThat(users).extracting(User::role)
                .containsExactly(Role.SINH_VIEN, Role.BAO_VE, Role.QUAN_LY, Role.CHU_TAI_KHOAN);
    }

    @Test
    void taiKhoanBiKhoaThiKhongDuocHoatDong() {
        Guard guard = Guard.create("baove01", "hash", "Trần Văn B", "0901234567");

        guard.lock();
        assertThatThrownBy(guard::ensureActive)
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED));

        guard.unlock();
        assertThatCode(guard::ensureActive).doesNotThrowAnyException();
    }

    @Test
    void mssvVaHoTenDuocChuanHoa() {
        Student student = Student.register("sv01", "hash", "  Nguyễn Văn A  ", " b21dccn001 ");

        assertThat(student.studentCode()).isEqualTo("B21DCCN001");
        assertThat(student.fullName()).isEqualTo("Nguyễn Văn A");
        assertThat(student.id()).isNull();
    }

    @Test
    void khongTaoDuocUserThieuTenDangNhap() {
        assertThatThrownBy(() -> Student.register(" ", "hash", "A", "B21DCCN001"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
