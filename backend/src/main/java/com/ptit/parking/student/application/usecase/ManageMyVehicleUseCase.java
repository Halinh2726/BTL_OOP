package com.ptit.parking.student.application.usecase;

import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.student.application.port.out.ParkingPresence;
import com.ptit.parking.student.application.port.out.VehicleRepository;
import com.ptit.parking.student.domain.Vehicle;
import java.time.Clock;

/**
 * Đợt 1 — sinh viên sửa loại xe hoặc ngừng sử dụng xe ("xóa xe"). Quy tắc chung của hai thao tác:
 * <ul>
 *   <li>Chỉ thao tác trên xe của CHÍNH MÌNH: xe của người khác → VEHICLE_NOT_FOUND (không tiết lộ xe đó tồn tại).</li>
 *   <li>Xe đang trong bãi → VEHICLE_PARKED: bảo vệ đang đối chiếu xe này, đổi dữ liệu giữa chừng sẽ gây nhầm lẫn.</li>
 *   <li>Xe đã ngừng → VEHICLE_INACTIVE (do {@link Vehicle} tự kiểm tra).</li>
 * </ul>
 */
public class ManageMyVehicleUseCase {

    public record ChangeTypeCommand(long studentId, long vehicleId, String vehicleTypeCode) {}

    public record DeactivateCommand(long studentId, long vehicleId) {}

    private final VehicleRepository vehicles;
    private final ParkingPresence parking;
    private final TransactionRunner transactions;
    private final Clock clock;

    public ManageMyVehicleUseCase(VehicleRepository vehicles, ParkingPresence parking,
                                  TransactionRunner transactions, Clock clock) {
        this.vehicles = vehicles;
        this.parking = parking;
        this.transactions = transactions;
        this.clock = clock;
    }

    public Vehicle changeType(ChangeTypeCommand command) {
        return transactions.inTransaction(() -> {
            Vehicle vehicle = ownedIdleVehicle(command.studentId(), command.vehicleId());
            if (vehicle.changeType(command.vehicleTypeCode())) {
                vehicles.update(vehicle);         // loại xe không tồn tại: fk_vehicles_type -> VALIDATION_ERROR
            }
            return vehicle;
        });
    }

    public Vehicle deactivate(DeactivateCommand command) {
        return transactions.inTransaction(() -> {
            Vehicle vehicle = ownedIdleVehicle(command.studentId(), command.vehicleId());
            vehicle.deactivate(clock.instant());
            vehicles.update(vehicle);
            return vehicle;
        });
    }

    private Vehicle ownedIdleVehicle(long studentId, long vehicleId) {
        Vehicle vehicle = vehicles.findById(vehicleId)
                .filter(v -> v.belongsTo(studentId))
                .orElseThrow(() -> new DomainException(ErrorCode.VEHICLE_NOT_FOUND));
        if (vehicle.isActive() && parking.isParked(vehicleId)) {
            throw new DomainException(ErrorCode.VEHICLE_PARKED);
        }
        return vehicle;
    }
}
