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
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * BV02 «include» HT01 (Đặc tả API #15) — bảo vệ xác nhận cho xe ra, hệ thống tự trừ phí. MỘT transaction:
 * khóa lượt gửi → còn DANG_GUI → phí tính LẠI theo giờ xác nhận (R03) → thanh toán (khóa 2 ví theo id tăng dần,
 * trừ sinh viên, cộng ví nhận — R06) → đóng lượt → lưu giao dịch. Bước nào lỗi thì rollback TOÀN BỘ:
 * không bao giờ có chuyện đã trừ điểm mà lượt chưa đóng, hay lượt đã đóng mà chưa trừ điểm (M3-05).
 * <p>Bọc trong {@link IdempotencyGuard} (M3-06): bảo vệ bấm "Xác nhận" hai lần, mạng gửi lại, hay hai request song
 * song cùng Idempotency-Key — sinh viên chỉ bị trừ MỘT lần.
 */
public class CheckOutUseCase {

    public record Command(long guardId, UUID idempotencyKey, long sessionId) {}

    /** {@code payment.createdAt()} chính là giờ xe ra: thanh toán và đóng lượt dùng chung MỘT thời điểm. */
    public record Result(ParkingPayment payment, boolean replayed) {}

    private final ParkingSessionRepository sessions;
    private final VehicleOwnerDirectory vehicles;
    private final PayParkingFeeUseCase payParkingFee;
    private final IdempotencyGuard idempotency;
    private final FeePolicy feePolicy;
    private final Clock clock;

    public CheckOutUseCase(ParkingSessionRepository sessions, VehicleOwnerDirectory vehicles,
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
        String requestHash = RequestFingerprint.of(TransactionType.THANH_TOAN, command.guardId(), command.sessionId());

        Execution<ParkingPayment> execution = idempotency.execute(command.idempotencyKey(), requestHash,
                ParkingPayment.class, () -> {
                    // Khóa lượt gửi: hai bảo vệ xác nhận cùng lượt thì người sau chờ, rồi thấy lượt đã đóng
                    ParkingSession session = sessions.lockById(command.sessionId())
                            .orElseThrow(() -> new DomainException(ErrorCode.NO_OPEN_SESSION));
                    if (!session.isOpen()) {
                        throw new DomainException(ErrorCode.SESSION_ALREADY_PAID);
                    }
                    VehicleOwner vehicle = vehicles.findByVehicleId(session.vehicleId()).orElseThrow();

                    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
                    session.close(command.guardId(), now);           // mọi kiểm tra của domain chạy TRƯỚC lần ghi đầu tiên
                    long fee = feePolicy.feeAt(now.atZone(clock.getZone()));          // R03: tính lại lúc xác nhận
                    ParkingPayment payment = payParkingFee.pay(new PayParkingFeeUseCase.Command(
                            session.id(), vehicle.studentUserId(), fee, command.guardId(),
                            command.idempotencyKey(), now));
                    sessions.saveExit(session);
                    return payment;                                  // IdempotencyGuard lưu giao dịch, cùng transaction
                });
        return new Result(execution.transaction(), execution.replayed());
    }
}
