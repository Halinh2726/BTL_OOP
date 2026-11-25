package com.ptit.parking.student.application.usecase;

import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.student.application.port.out.BicycleCodeIssuer;
import com.ptit.parking.student.application.port.out.VehicleRepository;
import com.ptit.parking.student.domain.PlateNumber;
import com.ptit.parking.student.domain.Vehicle;
import java.time.Clock;

/**
 * SV02 — sinh viên đăng ký xe (Đặc tả API #6).
 * <p>{@code studentId} LUÔN là người đang đăng nhập (controller lấy từ phiên) — không bao giờ nhận từ
 * request, nên sinh viên không thể đăng ký xe vào tên người khác.
 * <p>Đợt 1:
 * <ul>
 *   <li>Biển số phải đúng quy định (29A-1514, 29A-123.45...). Xe đạp để trống biển số → bãi cấp mã XD-xxxxx.</li>
 *   <li>Tối đa {@value Vehicle#MAX_ACTIVE_PER_STUDENT} xe đang dùng.</li>
 * </ul>
 * Một transaction: khóa dòng sinh viên (đếm xe) → cấp mã nếu cần → kiểm tra trùng → lưu.
 */
public class RegisterVehicleUseCase {

    /** Loại xe được bãi cấp mã khi không có biển số. */
    public static final String BICYCLE_TYPE = "XE_DAP";
    public static final String MOTORCYCLE_TYPE = "XE_MAY";
    public static final String ELECTRIC_MOTORCYCLE_TYPE = "XE_MAY_DIEN";

    /** {@code plateNumber} rỗng/null chỉ hợp lệ với xe đạp. */
    public record Command(long studentId, String plateNumber, String vehicleTypeCode) {}

    private final VehicleRepository vehicles;
    private final BicycleCodeIssuer bicycleCodes;
    private final TransactionRunner transactions;
    private final Clock clock;

    public RegisterVehicleUseCase(VehicleRepository vehicles, BicycleCodeIssuer bicycleCodes,
                                  TransactionRunner transactions, Clock clock) {
        this.vehicles = vehicles;
        this.bicycleCodes = bicycleCodes;
        this.transactions = transactions;
        this.clock = clock;
    }

    public Vehicle register(Command command) {
        boolean noPlate = command.plateNumber() == null || command.plateNumber().isBlank();
        if (noPlate && !BICYCLE_TYPE.equals(command.vehicleTypeCode())) {
            throw new DomainException(ErrorCode.PLATE_REQUIRED);
        }
        PlateNumber typed = noPlate ? null : PlateNumber.of(command.plateNumber());
        if (typed != null && !typed.isStandard()) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR);
        }
        if (typed != null && !matchesVehicleType(typed, command.vehicleTypeCode())) {
            throw new DomainException(ErrorCode.PLATE_TYPE_MISMATCH);
        }

        return transactions.inTransaction(() -> {
            Vehicle.ensureQuota(vehicles.countActiveForUpdate(command.studentId()));
            PlateNumber plate = typed != null ? typed : bicycleCodes.nextCode();
            if (vehicles.plateExists(plate)) {
                throw new DomainException(ErrorCode.PLATE_EXISTS);
            }
            // Loại xe không tồn tại: khóa ngoại fk_vehicles_type chặn, ConstraintErrorTranslator trả
            // VALIDATION_ERROR ở trường vehicleTypeCode. Hai người đăng ký cùng biển số cùng lúc:
            // UNIQUE uq_vehicles_active_plate chặn người thứ hai -> PLATE_EXISTS.
            return vehicles.save(Vehicle.register(command.studentId(), plate, command.vehicleTypeCode(),
                    clock.instant()));
        });
    }

    private static boolean matchesVehicleType(PlateNumber plate, String vehicleTypeCode) {
        return switch (vehicleTypeCode) {
            case BICYCLE_TYPE -> false; // xe đạp phải để trống, bãi tự cấp XD
            case MOTORCYCLE_TYPE -> plate.isMotorcyclePlate();
            case ELECTRIC_MOTORCYCLE_TYPE -> plate.isElectricMotorcyclePlate();
            default -> false;
        };
    }
}
