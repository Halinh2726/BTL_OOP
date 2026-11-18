package com.ptit.parking.student.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.student.application.usecase.ManageMyVehicleUseCase.ChangeTypeCommand;
import com.ptit.parking.student.application.usecase.ManageMyVehicleUseCase.DeactivateCommand;
import com.ptit.parking.student.domain.PlateNumber;
import com.ptit.parking.student.domain.Vehicle;
import com.ptit.parking.student.domain.VehicleStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Đợt 1 — sửa loại xe, ngừng sử dụng xe. */
class ManageMyVehicleUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-01T03:00:00Z");

    private final RegisterVehicleUseCaseTest.FakeVehicles vehicles = new RegisterVehicleUseCaseTest.FakeVehicles();
    private final Set<Long> parked = new HashSet<>();
    private final ManageMyVehicleUseCase manage = new ManageMyVehicleUseCase(vehicles, parked::contains,
            RegisterVehicleUseCaseTest.DIRECTLY, Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh")));
    private long vehicleId;

    @BeforeEach
    void coMotXeCuaSinhVien7() {
        vehicleId = vehicles.save(Vehicle.register(7L, PlateNumber.of("29A12345"), "XE_MAY", NOW)).id();
    }

    @Test
    void suaLoaiXe_thanhCong() {
        Vehicle changed = manage.changeType(new ChangeTypeCommand(7L, vehicleId, "XE_DAP_DIEN"));

        assertThat(changed.vehicleTypeCode()).isEqualTo("XE_DAP_DIEN");
    }

    @Test
    void ngungSuDung_giuLaiXe_ghiThoiDiem() {
        Vehicle stopped = manage.deactivate(new DeactivateCommand(7L, vehicleId));

        assertThat(stopped.status()).isEqualTo(VehicleStatus.NGUNG_SU_DUNG);
        assertThat(stopped.deactivatedAt()).isEqualTo(NOW);
        assertThat(vehicles.findById(vehicleId)).isPresent();          // không xóa hẳn
    }

    @Test
    void xeCuaNguoiKhac_bao404_khongTietLoXeTonTai() {
        assertThatThrownBy(() -> manage.deactivate(new DeactivateCommand(8L, vehicleId)))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.VEHICLE_NOT_FOUND));
    }

    @Test
    void xeDangTrongBai_khongSuaKhongNgungDuoc() {
        parked.add(vehicleId);

        assertThatThrownBy(() -> manage.changeType(new ChangeTypeCommand(7L, vehicleId, "XE_DAP")))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.VEHICLE_PARKED));
        assertThatThrownBy(() -> manage.deactivate(new DeactivateCommand(7L, vehicleId)))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.VEHICLE_PARKED));
    }

    @Test
    void xeDaNgung_khongThaoTacThemDuoc() {
        manage.deactivate(new DeactivateCommand(7L, vehicleId));

        assertThatThrownBy(() -> manage.deactivate(new DeactivateCommand(7L, vehicleId)))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.VEHICLE_INACTIVE));
        assertThatThrownBy(() -> manage.changeType(new ChangeTypeCommand(7L, vehicleId, "XE_DAP")))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.VEHICLE_INACTIVE));
    }
}
