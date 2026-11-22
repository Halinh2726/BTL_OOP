package com.ptit.parking.parking.application.usecase;

import com.ptit.parking.parking.application.event.LowBalanceWarning;
import com.ptit.parking.parking.application.event.VehicleCheckedOut;
import com.ptit.parking.parking.application.port.out.ParkingSessionRepository;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.VehicleOwner;
import com.ptit.parking.parking.domain.FeePolicy;
import com.ptit.parking.parking.domain.FeeQuote;
import com.ptit.parking.parking.domain.ParkingSession;
import com.ptit.parking.payment.application.IdempotencyGuard;
import com.ptit.parking.payment.application.IdempotencyGuard.Execution;
import com.ptit.parking.payment.application.RequestFingerprint;
import com.ptit.parking.payment.application.port.out.WalletRepository;
import com.ptit.parking.payment.application.usecase.PayParkingFeeUseCase;
import com.ptit.parking.payment.domain.ParkingPayment;
import com.ptit.parking.payment.domain.TransactionType;
import com.ptit.parking.shared.application.port.out.EventPublisher;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * BV02 «include» HT01 (Đặc tả API #15) — bảo vệ xác nhận cho xe ra, hệ thống tự trừ phí. MỘT transaction:
 * khóa lượt gửi → còn DANG_GUI → phí tính LẠI theo giờ xác nhận (R03) → thanh toán (khóa 2 ví theo id tăng dần,
 * trừ sinh viên, cộng ví nhận — R06) → đóng lượt → lưu giao dịch. Bước nào lỗi thì rollback TOÀN BỘ:
 * không bao giờ có chuyện đã trừ điểm mà lượt chưa đóng, hay lượt đã đóng mà chưa trừ điểm (M3-05).
 * <p>Bọc trong {@link IdempotencyGuard} (M3-06): bảo vệ bấm "Xác nhận" hai lần, mạng gửi lại, hay hai request song
 * song cùng Idempotency-Key — sinh viên chỉ bị trừ MỘT lần.
 * <p>Đợt 5: phí bằng 0 (xe có vé tháng — xem PassAwareFeePolicy) thì chỉ ĐÓNG lượt, không ghi giao dịch thanh toán.
 * Bảo vệ bấm lại thì lượt đã do chính bảo vệ đó đóng → trả lại kết quả cũ. Sau khi trả phí, số dư không đủ cho lượt
 * sau thì phát sự kiện nhắc sinh viên (mẫu Observer).
 */
public class CheckOutUseCase {

    public record Command(long guardId, UUID idempotencyKey, long sessionId) {}

    /** Xe ra miễn phí nhờ vé tháng: không có giao dịch; {@code balance} là số điểm (không đổi) của sinh viên. */
    public record FreeExit(long sessionId, Instant exitedAt, long balance, LocalDate passValidUntil) {}

    /**
     * Đúng MỘT trong hai: {@code payment} (trả theo lượt) hoặc {@code freeExit} (vé tháng).
     * {@code payment.createdAt()} chính là giờ xe ra: thanh toán và đóng lượt dùng chung MỘT thời điểm.
     */
    public record Result(ParkingPayment payment, boolean replayed, FreeExit freeExit) {

        public Result(ParkingPayment payment, boolean replayed) {
            this(payment, replayed, null);
        }
    }

    private final ParkingSessionRepository sessions;
    private final VehicleOwnerDirectory vehicles;
    private final PayParkingFeeUseCase payParkingFee;
    private final IdempotencyGuard idempotency;
    private final FeePolicy feePolicy;
    private final WalletRepository wallets;
    private final TransactionRunner transactions;
    private final EventPublisher events;
    private final Clock clock;

    public CheckOutUseCase(ParkingSessionRepository sessions, VehicleOwnerDirectory vehicles,
                           PayParkingFeeUseCase payParkingFee, IdempotencyGuard idempotency, FeePolicy feePolicy,
                           WalletRepository wallets, TransactionRunner transactions, EventPublisher events, Clock clock) {
        this.sessions = sessions;
        this.vehicles = vehicles;
        this.payParkingFee = payParkingFee;
        this.idempotency = idempotency;
        this.feePolicy = feePolicy;
        this.wallets = wallets;
        this.transactions = transactions;
        this.events = events;
        this.clock = clock;
    }

    public Result checkOut(Command command) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        // Báo giá bằng một lần đọc KHÔNG khóa, ngoài transaction, chỉ để chọn đường đi. Không thấy lượt thì đi đường
        // trả phí: IdempotencyGuard kiểm tra key TRƯỚC (giữ nguyên thứ tự lỗi cũ), rồi tới NO_OPEN_SESSION.
        FeeQuote quote = sessions.findById(command.sessionId())
                .map(session -> feePolicy.quote(session.vehicleId(), now.atZone(clock.getZone())))
                .orElse(null);
        if (quote != null && quote.isFree()) {
            return freeExit(command, now, quote);
        }
        return paidExit(command);
    }

    private Result freeExit(Command command, Instant now, FeeQuote quote) {
        return transactions.inTransaction(() -> {
            ParkingSession session = sessions.lockById(command.sessionId())
                    .orElseThrow(() -> new DomainException(ErrorCode.NO_OPEN_SESSION));
            VehicleOwner vehicle = vehicles.findByVehicleId(session.vehicleId()).orElseThrow();
            long balance = wallets.findByUserId(vehicle.studentUserId()).orElseThrow().balance();
            if (!session.isOpen()) {
                // Bấm lại: lượt đã do CHÍNH bảo vệ này đóng (miễn phí) -> trả kết quả cũ; người khác -> đã thanh toán
                if (session.exitGuardId() != null && session.exitGuardId() == command.guardId()) {
                    return new Result(null, true,
                            new FreeExit(session.id(), session.exitedAt(), balance, quote.passValidUntil()));
                }
                throw new DomainException(ErrorCode.SESSION_ALREADY_PAID);
            }
            session.close(command.guardId(), now);
            sessions.saveExit(session);
            events.publish(new VehicleCheckedOut(vehicle.studentUserId(), vehicle.plate().display(), 0, balance,
                    quote.passValidUntil(), now, clock.getZone()));
            return new Result(null, false, new FreeExit(session.id(), now, balance, quote.passValidUntil()));
        });
    }

    private Result paidExit(Command command) {
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
                    long fee = feePolicy.quote(vehicle.vehicleId(), now.atZone(clock.getZone())).points();   // R03
                    ParkingPayment payment = payParkingFee.pay(new PayParkingFeeUseCase.Command(
                            session.id(), vehicle.studentUserId(), fee, command.guardId(),
                            command.idempotencyKey(), now));
                    sessions.saveExit(session);
                    // Phát sự kiện xe ra trước, rồi mới tới cảnh báo số dư thấp để thông báo cảnh báo nằm trên cùng
                    events.publish(new VehicleCheckedOut(vehicle.studentUserId(), vehicle.plate().display(),
                            payment.points(), payment.sourceBalanceAfter(), null, now, clock.getZone()));
                    long nextMinimum = feePolicy.minimumFeeFor(vehicle.vehicleId(), now.atZone(clock.getZone()));
                    if (nextMinimum > 0 && payment.sourceBalanceAfter() < nextMinimum * 2) {
                        events.publish(new LowBalanceWarning(vehicle.studentUserId(), payment.sourceBalanceAfter(),
                                nextMinimum));                       // chỉ gửi nếu transaction commit
                    }
                    return payment;                                  // IdempotencyGuard lưu giao dịch, cùng transaction
                });
        return new Result(execution.transaction(), execution.replayed());
    }
}
