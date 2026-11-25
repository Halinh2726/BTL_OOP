package com.ptit.parking.student.application.usecase;

import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.student.application.port.out.ParkingPresence;
import com.ptit.parking.student.application.port.out.VehicleRepository;
import com.ptit.parking.student.domain.Vehicle;
import java.time.Clock;

/**
 * Sinh viên ngừng sử dụng xe đã đăng ký. Quy tắc:
 * <ul>
 *   <li>Chỉ thao tác trên xe của CHÍNH MÌNH: xe của người khác → VEHICLE_NOT_FOUND (không tiết lộ xe đó tồn tại).</li>
 *   <li>Xe đang trong bãi → VEHICLE_PARKED: bảo vệ đang đối chiếu xe này, không thể ngừng giữa chừng.</li>
 *   <li>Xe đã ngừng → VEHICLE_INACTIVE (do {@link Vehicle} tự kiểm tra).</li>
 * </ul>
 */
public class ManageMyVehicleUseCase {

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
