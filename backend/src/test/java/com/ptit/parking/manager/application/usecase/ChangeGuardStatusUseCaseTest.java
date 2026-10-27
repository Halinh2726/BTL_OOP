package com.ptit.parking.manager.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.ptit.parking.auth.domain.AccountStatus;
import com.ptit.parking.auth.domain.Guard;
import com.ptit.parking.manager.application.port.out.InMemoryGuardAccountRepository;
import com.ptit.parking.manager.application.usecase.ChangeGuardStatusUseCase.Command;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** QL03, QL04 — danh sách bảo vệ, khóa / mở khóa. Chỉ khóa được BẢO VỆ, bấm lại vẫn thành công, không xóa. */
class ChangeGuardStatusUseCaseTest {

    private final InMemoryGuardAccountRepository guards = new InMemoryGuardAccountRepository();
    private final long tran = guards.insert(new Guard(null, "baove01", "hash", "Trần Văn B",
            AccountStatus.HOAT_DONG, "0901234567"));
    private final long an = guards.insert(new Guard(null, "baove02", "hash", "An Thị D",
            AccountStatus.BI_KHOA, "0907654321"));
    private final TransactionRunner directly = new TransactionRunner() {
        @Override
        public <T> T inTransaction(Supplier<T> work) {
            return work.get();
        }
    };

    private final ChangeGuardStatusUseCase changeStatus = new ChangeGuardStatusUseCase(guards, directly);
    private final ListGuardsUseCase listGuards = new ListGuardsUseCase(guards);

    @Test
    void danhSach_coCaNguoiBiKhoa_sapTheoHoTen() {
        assertThat(listGuards.list())
                .extracting(Guard::username, Guard::status)
                .containsExactly(
                        tuple("baove02", AccountStatus.BI_KHOA),          // "An..." đứng trước "Trần..."
                        tuple("baove01", AccountStatus.HOAT_DONG));
    }

    @Test
    void khoaBaoVeDangHoatDong_BI_KHOA_daLuu() {
        Guard guard = changeStatus.change(new Command(tran, AccountStatus.BI_KHOA));

        assertThat(guard.status()).isEqualTo(AccountStatus.BI_KHOA);
        assertThat(guard.username()).isEqualTo("baove01");
        assertThat(guards.stored(tran).status()).isEqualTo(AccountStatus.BI_KHOA);       // trạng thái ĐÃ LƯU
        assertThat(guards.stored(an).status()).isEqualTo(AccountStatus.BI_KHOA);         // người khác giữ nguyên
    }

    @Test
    void moKhoaBaoVeDangBiKhoa_HOAT_DONG_daLuu() {
        Guard guard = changeStatus.change(new Command(an, AccountStatus.HOAT_DONG));

        assertThat(guard.status()).isEqualTo(AccountStatus.HOAT_DONG);
        assertThat(guards.stored(an).status()).isEqualTo(AccountStatus.HOAT_DONG);
    }

    @Test
    void khoaNguoiDaBiKhoa_vanThanhCong_khongGhiGi() {
        Guard guard = changeStatus.change(new Command(an, AccountStatus.BI_KHOA));

        assertThat(guard.status()).isEqualTo(AccountStatus.BI_KHOA);
        assertThat(guards.statusWrites()).isZero();
    }

    @Test
    void idKhongPhaiBaoVe_GUARD_NOT_FOUND_khongGhiGi() {
        assertThatThrownBy(() -> changeStatus.change(new Command(999L, AccountStatus.BI_KHOA)))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.GUARD_NOT_FOUND));
        assertThat(guards.statusWrites()).isZero();
    }

    @Test
    void khoaRoiMoKhoa_khongXoaTaiKhoan() {
        changeStatus.change(new Command(tran, AccountStatus.BI_KHOA));
        changeStatus.change(new Command(tran, AccountStatus.HOAT_DONG));

        assertThat(guards.all()).hasSize(2);                                              // R14: chỉ khóa, không xóa
        assertThat(guards.stored(tran).status()).isEqualTo(AccountStatus.HOAT_DONG);
    }
}
