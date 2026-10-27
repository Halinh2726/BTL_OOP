package com.ptit.parking.student.application.usecase;

import java.time.Clock;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.student.application.port.out.VehicleRepository;
import com.ptit.parking.student.domain.PlateNumber;
import com.ptit.parking.student.domain.Vehicle;

/**
 * SV02 — sinh viên đăng ký xe (Đặc tả API #6).
 * <p>{@code studentId} LUÔN là người đang đăng nhập (controller lấy từ phiên) — không bao giờ nhận từ
 * request, nên sinh viên không thể đăng ký xe vào tên người khác.
 */
public class RegisterVehicleUseCase {

    public record Command(long studentId, String plateNumber, String vehicleTypeCode) {}

    private final VehicleRepository vehicles;
    private final Clock clock;

    public RegisterVehicleUseCase(VehicleRepository vehicles, Clock clock) {
        this.vehicles = vehicles;
        this.clock = clock;
    }

    public Vehicle register(Command command) {
        PlateNumber plate = PlateNumber.of(command.plateNumber());
        if (vehicles.plateExists(plate)) {
            throw new DomainException(ErrorCode.PLATE_EXISTS);
        }
        // Loại xe không tồn tại: khóa ngoại fk_vehicles_type chặn, ConstraintErrorTranslator trả
        // VALIDATION_ERROR ở trường vehicleTypeCode. Hai người đăng ký cùng biển số cùng lúc:
        // UNIQUE uq_vehicles_plate chặn người thứ hai -> PLATE_EXISTS.
        return vehicles.save(Vehicle.register(command.studentId(), plate, command.vehicleTypeCode(), clock.instant()));
    }
}