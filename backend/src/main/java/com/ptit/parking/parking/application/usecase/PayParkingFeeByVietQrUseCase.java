package com.ptit.parking.parking.application.usecase;

import com.ptit.parking.parking.application.port.out.ParkingSessionRepository;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.VehicleOwner;
import com.ptit.parking.parking.domain.FeePolicy;
import com.ptit.parking.parking.domain.ParkingSession;
import com.ptit.parking.payment.application.IdempotencyGuard;
import com.ptit.parking.payment.application.IdempotencyGuard.Execution;
import com.ptit.parking.payment.application.RequestFingerprint;
import com.ptit.parking.payment.application.port.out.ReceivingWalletProvider;
import com.ptit.parking.payment.application.port.out.WalletRepository;
import com.ptit.parking.payment.domain.ParkingPayment;
import com.ptit.parking.payment.domain.TransactionType;
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BV02-VietQR — Xác nhận thanh toán phí gửi xe bằng chuyển khoản VietQR khi sinh viên không đủ điểm ví.
 * <p>MỘT transaction có {@link IdempotencyGuard} bảo vệ:
 * Khóa lượt gửi -> tính phí theo giờ ra -> ghi nhận nạp đối ứng VietQR và trừ phí vào ví nhận chủ bãi xe ->
 * đóng lượt gửi DA_RA -> lưu giao dịch THANH_TOAN.
 */
public class PayParkingFeeByVietQrUseCase {

    public record Command(long guardId, UUID idempotencyKey, long sessionId) {}

    public record Result(ParkingPayment payment, VehicleOwner vehicleOwner, boolean replayed) {}

    private final ParkingSessionRepository sessions;
    private final VehicleOwnerDirectory vehicles;
    private final WalletRepository wallets;
    private final ReceivingWalletProvider receivingWallet;
    private final IdempotencyGuard idempotency;
    private final FeePolicy feePolicy;
    private final Clock clock;

    public PayParkingFeeByVietQrUseCase(ParkingSessionRepository sessions, VehicleOwnerDirectory vehicles,
                                        WalletRepository wallets, ReceivingWalletProvider receivingWallet,
                                        IdempotencyGuard idempotency, FeePolicy feePolicy, Clock clock) {
        this.sessions = sessions;
        this.vehicles = vehicles;
        this.wallets = wallets;
        this.receivingWallet = receivingWallet;
        this.idempotency = idempotency;
        this.feePolicy = feePolicy;
        this.clock = clock;
    }

    public Result payByVietQr(Command command) {
        String requestHash = RequestFingerprint.of(TransactionType.THANH_TOAN, command.guardId(), "VIETQR", command.sessionId());

        Execution<ParkingPayment> execution = idempotency.execute(command.idempotencyKey(), requestHash,
                ParkingPayment.class, () -> {
                    ParkingSession session = sessions.lockById(command.sessionId())
                            .orElseThrow(() -> new DomainException(ErrorCode.NO_OPEN_SESSION));
                    if (!session.isOpen()) {
                        throw new DomainException(ErrorCode.SESSION_ALREADY_PAID);
                    }
                    VehicleOwner vehicle = vehicles.findByVehicleId(session.vehicleId()).orElseThrow();

                    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
                    session.close(command.guardId(), now);
                    long fee = feePolicy.feeAt(now.atZone(clock.getZone()));

                    long studentWalletId = wallets.findByUserId(vehicle.studentUserId()).orElseThrow().id();
                    long receiverWalletId = receivingWallet.receivingWalletId();

                    Map<Long, Wallet> locked = wallets.lockInIdOrder(List.of(studentWalletId, receiverWalletId));
                    Wallet student = locked.get(studentWalletId);
                    Wallet receiver = locked.get(receiverWalletId);

                    // Sinh viên đã chuyển khoản thật qua VietQR: ghi nhận nạp đối ứng đúng số tiền phí rồi trừ vào ví nhận
                    student.credit(fee);
                    ParkingPayment payment = ParkingPayment.execute(session.id(), student, receiver, fee,
                            command.guardId(), command.idempotencyKey(), now);
                    wallets.save(student);
                    wallets.save(receiver);

                    sessions.saveExit(session);
                    return payment;
                });

        ParkingSession session = sessions.lockById(execution.transaction().sessionId()).orElse(null);
        VehicleOwner vehicleOwner = session == null ? null : vehicles.findByVehicleId(session.vehicleId()).orElse(null);
        return new Result(execution.transaction(), vehicleOwner, execution.replayed());
    }
}

