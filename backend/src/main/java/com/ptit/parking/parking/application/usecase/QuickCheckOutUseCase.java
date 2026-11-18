package com.ptit.parking.parking.application.usecase;

import com.ptit.parking.parking.application.port.out.ParkingSessionRepository;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.VehicleOwner;
import com.ptit.parking.parking.domain.FeePolicy;
import com.ptit.parking.parking.domain.ParkingSession;
import com.ptit.parking.payment.application.IdempotencyGuard;
import com.ptit.parking.payment.application.IdempotencyGuard.Execution;
import com.ptit.parking.payment.application.RequestFingerprint;
import com.ptit.parking.payment.application.usecase.PayParkingFeeUseCase;
import com.ptit.parking.payment.domain.ParkingPayment;
import com.ptit.parking.payment.domain.TransactionType;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.student.domain.PlateNumber;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * BV02-Quick — Cho xe ra và tự động thanh toán 1 chạm (One-Scan Check-out) qua mã quét biển số / QR.
 * <p>MỘT transaction có {@link IdempotencyGuard} bảo vệ:
 * Khóa xe theo biển số -> tìm lượt đang mở -> khóa lượt gửi -> tính phí theo giờ ra (R03) ->
 * trừ điểm ví sinh viên & cộng điểm ví chủ bãi xe (R06) -> đóng lượt gửi -> lưu giao dịch.
 * Nếu không đủ điểm hoặc lỗi, toàn bộ thao tác được rollback an toàn (M3-05).
 */
public class QuickCheckOutUseCase {

    public record Command(long guardId, UUID idempotencyKey, String plateNumber) {}

    public record Result(ParkingPayment payment, VehicleOwner vehicleOwner, boolean replayed) {}

    private final ParkingSessionRepository sessions;
    private final VehicleOwnerDirectory vehicles;
    private final PayParkingFeeUseCase payParkingFee;
    private final IdempotencyGuard idempotency;
    private final FeePolicy feePolicy;
    private final Clock clock;

    public QuickCheckOutUseCase(ParkingSessionRepository sessions, VehicleOwnerDirectory vehicles,
                                PayParkingFeeUseCase payParkingFee, IdempotencyGuard idempotency,
                                FeePolicy feePolicy, Clock clock) {
        this.sessions = sessions;
        this.vehicles = vehicles;
        this.payParkingFee = payParkingFee;
        this.idempotency = idempotency;
        this.feePolicy = feePolicy;
        this.clock = clock;
    }

    public Result checkOut(Command command) {
        PlateNumber plate = PlateNumber.tryParse(command.plateNumber())
                .orElseThrow(() -> new DomainException(ErrorCode.VALIDATION_ERROR));

        String requestHash = RequestFingerprint.of(TransactionType.THANH_TOAN, command.guardId(), plate.value());

        Execution<ParkingPayment> execution = idempotency.execute(command.idempotencyKey(), requestHash,
                ParkingPayment.class, () -> {
                    VehicleOwner vehicle = vehicles.lockByPlate(plate)
                            .orElseThrow(() -> new DomainException(ErrorCode.PLATE_NOT_FOUND));

                    ParkingSession openSession = sessions.findOpenByVehicle(vehicle.vehicleId())
                            .orElseThrow(() -> new DomainException(ErrorCode.NO_OPEN_SESSION));

                    ParkingSession session = sessions.lockById(openSession.id())
                            .orElseThrow(() -> new DomainException(ErrorCode.NO_OPEN_SESSION));
                    if (!session.isOpen()) {
                        throw new DomainException(ErrorCode.SESSION_ALREADY_PAID);
                    }

                    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
                    session.close(command.guardId(), now);
                    long fee = feePolicy.feeAt(now.atZone(clock.getZone()));
                    ParkingPayment payment = payParkingFee.pay(new PayParkingFeeUseCase.Command(
                            session.id(), vehicle.studentUserId(), fee, command.guardId(),
                            command.idempotencyKey(), now));
                    sessions.saveExit(session);
                    return payment;
                });

        VehicleOwner vehicleOwner = vehicles.findByPlate(plate).orElse(null);
        return new Result(execution.transaction(), vehicleOwner, execution.replayed());
    }
}

