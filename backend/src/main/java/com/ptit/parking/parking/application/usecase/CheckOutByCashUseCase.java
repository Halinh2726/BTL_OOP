package com.ptit.parking.parking.application.usecase;

import com.ptit.parking.catalog.application.port.out.ExchangeRateRepository;
import com.ptit.parking.parking.application.port.out.GateCashRepository;
import com.ptit.parking.parking.application.port.out.OpenShiftLookup;
import com.ptit.parking.parking.application.port.out.ParkingSessionRepository;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.VehicleOwner;
import com.ptit.parking.parking.domain.FeePolicy;
import com.ptit.parking.parking.domain.FeeQuote;
import com.ptit.parking.parking.domain.GateCashCollection;
import com.ptit.parking.parking.domain.ParkingSession;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * BV02-Tiền mặt (Mục 3.5, 3.6 PDF): bảo vệ thu tiền mặt tại chốt khi cho xe ra.
 * <ul>
 *   <li>Mức tiền do backend tự tính theo biểu phí và tỷ lệ quy đổi; bảo vệ không nhập hay sửa số tiền (Mục 3.8).</li>
 *   <li>Kiểm tra giờ khóa (mặc định 22:00) và áp dụng phí qua đêm (15.000đ) nếu lượt qua ngày.</li>
 *   <li>Tiền mặt chỉ ghi vào {@code gate_cash_collections} gắn với ca trực, KHÔNG cộng ví điểm và KHÔNG ghi
 *       {@code point_transactions} để giữ nguyên bất biến đối soát R12.</li>
 *   <li>An toàn idempotency: bấm nhiều lần cùng {@code idempotencyKey} trả lại đúng khoản thu đã lưu.</li>
 * </ul>
 */
public class CheckOutByCashUseCase {

    public record Command(long guardId, UUID idempotencyKey, long sessionId) {}

    public record Result(GateCashCollection collection, VehicleOwner vehicle, boolean replayed) {}

    private final ParkingSessionRepository sessions;
    private final VehicleOwnerDirectory vehicles;
    private final GateCashRepository gateCash;
    private final OpenShiftLookup openShiftLookup;
    private final ExchangeRateRepository exchangeRates;
    private final FeePolicy feePolicy;
    private final TransactionRunner transactions;
    private final Clock clock;

    public CheckOutByCashUseCase(ParkingSessionRepository sessions, VehicleOwnerDirectory vehicles,
                                 GateCashRepository gateCash, OpenShiftLookup openShiftLookup,
                                 ExchangeRateRepository exchangeRates, FeePolicy feePolicy,
                                 TransactionRunner transactions, Clock clock) {
        this.sessions = sessions;
        this.vehicles = vehicles;
        this.gateCash = gateCash;
        this.openShiftLookup = openShiftLookup;
        this.exchangeRates = exchangeRates;
        this.feePolicy = feePolicy;
        this.transactions = transactions;
        this.clock = clock;
    }

    public Result checkOutByCash(Command command) {
        return transactions.inTransaction(() -> {
            Optional<GateCashCollection> existing = gateCash.findByIdempotencyKey(command.idempotencyKey());
            if (existing.isPresent()) {
                GateCashCollection prev = existing.get();
                if (prev.guardId() != command.guardId() || prev.sessionId() != command.sessionId()) {
                    throw new DomainException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
                }
                ParkingSession prevSession = sessions.findById(prev.sessionId()).orElseThrow();
                VehicleOwner prevOwner = vehicles.findByVehicleId(prevSession.vehicleId()).orElseThrow();
                return new Result(prev, prevOwner, true);
            }

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

            long cashPerPoint = exchangeRates.currentExchangeRate().cashPerPoint();
            long cashAmount = Math.multiplyExact(quote.points(), cashPerPoint);
            Long shiftId = openShiftLookup.findOpenShiftId(command.guardId()).orElse(null);

            session.close(command.guardId(), now);
            sessions.saveExit(session);

            GateCashCollection saved = gateCash.save(GateCashCollection.create(
                    session.id(), command.guardId(), shiftId, cashAmount,
                    quote.overnight(), command.idempotencyKey(), now));
            return new Result(saved, vehicle, false);
        });
    }
}
