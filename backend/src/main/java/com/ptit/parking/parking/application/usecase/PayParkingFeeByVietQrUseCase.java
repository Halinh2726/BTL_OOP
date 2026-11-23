package com.ptit.parking.parking.application.usecase;

import com.ptit.parking.catalog.application.port.out.ExchangeRateRepository;
import com.ptit.parking.parking.application.port.out.ParkingSessionRepository;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.VehicleOwner;
import com.ptit.parking.parking.domain.FeePolicy;
import com.ptit.parking.parking.domain.FeeQuote;
import com.ptit.parking.parking.domain.ParkingSession;
import com.ptit.parking.payment.application.IdempotencyGuard;
import com.ptit.parking.payment.application.IdempotencyGuard.Execution;
import com.ptit.parking.payment.application.RequestFingerprint;
import com.ptit.parking.payment.application.port.out.PointTransactionRepository;
import com.ptit.parking.payment.application.port.out.ReceivingWalletProvider;
import com.ptit.parking.payment.application.port.out.WalletRepository;
import com.ptit.parking.payment.domain.CashDeposit;
import com.ptit.parking.payment.domain.ExchangeRate;
import com.ptit.parking.payment.domain.ParkingPayment;
import com.ptit.parking.payment.domain.TransactionType;
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BV02-VietQR — Xác nhận thanh toán phí gửi xe bằng chuyển khoản VietQR khi sinh viên không đủ điểm ví.
 * <p>MỘT transaction có {@link IdempotencyGuard} bảo vệ:
 * kiểm tra giờ khóa -> khóa lượt gửi -> tính phí (gồm phí qua đêm) -> ghi nhận giao dịch nạp đối ứng
 * {@link CashDeposit} (giữ bất biến bảo toàn điểm R12) -> trừ ví sinh viên và cộng ví chủ bãi qua
 * {@link ParkingPayment} -> đóng lượt gửi DA_RA.
 */
public class PayParkingFeeByVietQrUseCase {

    public record Command(long guardId, UUID idempotencyKey, long sessionId) {}

    public record Result(ParkingPayment payment, VehicleOwner vehicleOwner, boolean replayed) {}

    private final ParkingSessionRepository sessions;
    private final VehicleOwnerDirectory vehicles;
    private final WalletRepository wallets;
    private final ReceivingWalletProvider receivingWallet;
    private final PointTransactionRepository pointTransactions;
    private final ExchangeRateRepository exchangeRates;
    private final IdempotencyGuard idempotency;
    private final FeePolicy feePolicy;
    private final Clock clock;

    public PayParkingFeeByVietQrUseCase(ParkingSessionRepository sessions, VehicleOwnerDirectory vehicles,
                                        WalletRepository wallets, ReceivingWalletProvider receivingWallet,
                                        PointTransactionRepository pointTransactions,
                                        ExchangeRateRepository exchangeRates,
                                        IdempotencyGuard idempotency, FeePolicy feePolicy, Clock clock) {
        this.sessions = sessions;
        this.vehicles = vehicles;
        this.wallets = wallets;
        this.receivingWallet = receivingWallet;
        this.pointTransactions = pointTransactions;
        this.exchangeRates = exchangeRates;
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
                    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
                    ZonedDateTime nowZoned = now.atZone(clock.getZone());
                    if (feePolicy.isCheckoutLocked(nowZoned)) {
                        throw new DomainException(ErrorCode.CHECKOUT_CURFEW);
                    }

                    VehicleOwner vehicle = vehicles.findByVehicleId(session.vehicleId()).orElseThrow();
                    FeeQuote quote = feePolicy.quoteSession(vehicle.vehicleId(), session.enteredAt(), nowZoned);
                    if (quote.isFree()) {
                        throw new DomainException(ErrorCode.INVALID_AMOUNT);
                    }
                    long fee = quote.points();

                    session.close(command.guardId(), now);

                    long studentWalletId = wallets.findByUserId(vehicle.studentUserId()).orElseThrow().id();
                    long receiverWalletId = receivingWallet.receivingWalletId();

                    Map<Long, Wallet> locked = wallets.lockInIdOrder(List.of(studentWalletId, receiverWalletId));
                    Wallet student = locked.get(studentWalletId);
                    Wallet receiver = locked.get(receiverWalletId);

                    // Ghi nhận nạp điểm đối ứng từ chuyển khoản VietQR để bảo toàn sổ cái R12 (v_reconciliation)
                    ExchangeRate rate = exchangeRates.currentExchangeRate();
                    long cashAmount = fee * rate.cashPerPoint();
                    UUID depositKey = UUID.nameUUIDFromBytes(
                            ("VIETQR_DEPOSIT:" + command.idempotencyKey()).getBytes(StandardCharsets.UTF_8));
                    String depositHash = RequestFingerprint.of(
                            TransactionType.DOI_DIEM, command.guardId(), "VIETQR", session.id(), fee);
                    CashDeposit deposit = CashDeposit.execute(student, cashAmount, rate, command.guardId(), depositKey, now);
                    pointTransactions.save(deposit, depositHash);

                    ParkingPayment payment = ParkingPayment.execute(session.id(), student, receiver, fee,
                            command.guardId(), command.idempotencyKey(), now);
                    wallets.save(student);
                    wallets.save(receiver);

                    sessions.saveExit(session);
                    return payment;
                });

        ParkingSession session = sessions.findById(execution.transaction().sessionId()).orElse(null);
        VehicleOwner vehicleOwner = session == null ? null : vehicles.findByVehicleId(session.vehicleId()).orElse(null);
        return new Result(execution.transaction(), vehicleOwner, execution.replayed());
    }
}
