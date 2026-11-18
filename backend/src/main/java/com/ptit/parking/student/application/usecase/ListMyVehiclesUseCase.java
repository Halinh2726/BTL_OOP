package com.ptit.parking.student.application.usecase;

import com.ptit.parking.student.application.port.out.VehicleRepository;
import com.ptit.parking.student.domain.Vehicle;
import java.util.List;

/**
 * SV02 — xem danh sách xe của tôi (Đặc tả API #5): chỉ xe của người đang đăng nhập, mới đăng ký lên đầu.
 * Đợt 1: xe đang dùng lên trước, xe đã ngừng sử dụng xuống cuối.
 */
public class ListMyVehiclesUseCase {

    private final VehicleRepository vehicles;

    public ListMyVehiclesUseCase(VehicleRepository vehicles) {
        this.vehicles = vehicles;
    }

    public List<Vehicle> list(long studentId) {
        return vehicles.findByStudentId(studentId).stream().sorted(Vehicle.ACTIVE_FIRST).toList();
    }
}