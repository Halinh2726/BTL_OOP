package com.ptit.parking.student.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.ptit.parking.student.domain.PlateNumber;
import com.ptit.parking.student.domain.Vehicle;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** SV02 — danh sách xe của tôi. Dùng lại kho xe giả của RegisterVehicleUseCaseTest. */
class ListMyVehiclesUseCaseTest {

    private final RegisterVehicleUseCaseTest.FakeVehicles vehicles = new RegisterVehicleUseCaseTest.FakeVehicles();
    private final ListMyVehiclesUseCase listMyVehicles = new ListMyVehiclesUseCase(vehicles);

    @Test
    void chiTraXeCuaMinh_xeMoiDangKyLenDau() {
        vehicles.save(Vehicle.register(7L, PlateNumber.of("29A11111"), "XE_MAY", Instant.parse("2026-10-01T01:00:00Z")));
        vehicles.save(Vehicle.register(8L, PlateNumber.of("29A22222"), "XE_MAY", Instant.parse("2026-10-02T01:00:00Z")));
        vehicles.save(Vehicle.register(7L, PlateNumber.of("29A33333"), "XE_DAP", Instant.parse("2026-10-03T01:00:00Z")));

        assertThat(listMyVehicles.list(7L))
                .extracting(vehicle -> vehicle.plate().value())
                .containsExactly("29A33333", "29A11111");            // không có xe 29A22222 của sinh viên 8
    }

    @Test
    void chuaCoXe_traDanhSachRong() {
        assertThat(listMyVehicles.list(7L)).isEmpty();
    }
}