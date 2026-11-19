package com.ptit.parking.pass.application.usecase;

import com.ptit.parking.pass.application.event.PassPurchased;
import com.ptit.parking.pass.application.port.out.MonthlyPassRepository;
import com.ptit.parking.pass.application.port.out.PassCatalog;
import com.ptit.parking.pass.application.port.out.PassCatalog.VehicleInfo;
import com.ptit.parking.pass.domain.MonthlyPass;
import com.ptit.parking.payment.application.RequestFingerprint;
import com.ptit.parking.payment.application.port.out.PointTransactionRepository;
import com.ptit.parking.payment.application.port.out.ReceivingWalletProvider;
import com.ptit.parking.payment.application.port.out.WalletRepository;
import com.ptit.parking.payment.domain.PassPurchase;
import com.ptit.parking.payment.domain.TransactionType;
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.application.port.out.EventPublisher;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.student.domain.PlateNumber;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Đợt 5 — sinh viên mua vé tháng cho một xe của mình bằng điểm. MỘT transaction: kiểm tra chưa có vé cho xe / tháng
 * đó → khóa hai ví theo id tăng dần → trừ sinh viên, cộng chủ bãi (giao dịch MUA_VE) → ghi vé. Bước nào lỗi thì
 * rollback toàn bộ: không có chuyện đã trừ điểm mà không có vé.
 * <p>Bấm "Mua" hai lần với CÙNG Idempotency-Key: lần sau thấy vé đã có và chính giao dịch mang key đó → trả kết quả
 * cũ, không trừ điểm lần hai. Hai request KHÁC key cùng lúc: UNIQUE (xe, tháng) chặn request sau.
 */
public class BuyPassUseCase {

    public record Command(long studentId, UUID idempotencyKey, long vehicleId, YearMonth month) {}

    public record Purchase(MonthlyPass pass, VehicleInfo vehicle, long balanceAfter, boolean replayed) {}

    /** Một xe trong màn hình mua vé: giá (null nếu loại xe chưa bán vé), vé tháng này / tháng sau nếu đã có. */
    public record Option(VehicleInfo vehicle, Long price, MonthlyPass current, MonthlyPass next) {}

    public record Overview(YearMonth currentMonth, YearMonth nextMonth, List<Option> options, List<Owned> passes) {}

    public record Owned(MonthlyPass pass, VehicleInfo vehicle) {}

    private final MonthlyPassRepository passes;
    private final PassCatalog catalog;
    private final WalletRepository wallets;
    private final ReceivingWalletProvider ownerWallet;
    private final PointTransactionRepository ledger;
    private final TransactionRunner transactions;
    private final EventPublisher events;
    private final Clock clock;

    public BuyPassUseCase(MonthlyPassRepository passes, PassCatalog catalog, WalletRepository wallets,
                          ReceivingWalletProvider ownerWallet, PointTransactionRepository ledger,
                          TransactionRunner transactions, EventPublisher events, Clock clock) {
        this.passes = passes;
        this.catalog = catalog;
        this.wallets = wallets;
        this.ownerWallet = ownerWallet;
        this.ledger = ledger;
        this.transactions = transactions;
        this.events = events;
        this.clock = clock;
    }

    public Overview overview(long studentId) {
        YearMonth current = YearMonth.now(clock);
        List<VehicleInfo> vehicles = catalog.vehiclesOf(studentId);
        List<Option> options = vehicles.stream().filter(VehicleInfo::active)
                .map(v -> new Option(v, catalog.price(v.vehicleTypeCode()).orElse(null),
                        passes.findFor(v.id(), current).orElse(null), passes.findFor(v.id(), current.plusMonths(1)).orElse(null)))
                .toList();
        Map<Long, VehicleInfo> byId = vehicles.stream().collect(Collectors.toMap(VehicleInfo::id, Function.identity()));
        List<Owned> owned = passes.findByStudent(studentId).stream()
                .map(p -> new Owned(p, byId.get(p.vehicleId()))).toList();
        return new Overview(current, current.plusMonths(1), options, owned);
    }

    public Purchase buy(Command command) {
        MonthlyPass.requirePurchasable(command.month(), LocalDate.now(clock));
        VehicleInfo vehicle = catalog.vehiclesOf(command.studentId()).stream()
                .filter(v -> v.id() == command.vehicleId()).findFirst()
                .orElseThrow(() -> new DomainException(ErrorCode.VEHICLE_NOT_FOUND));   // xe của người khác: như không có
        if (!vehicle.active()) {
            throw new DomainException(ErrorCode.VEHICLE_INACTIVE);
        }
        long price = catalog.price(vehicle.vehicleTypeCode())
                .orElseThrow(() -> new DomainException(ErrorCode.PASS_NOT_AVAILABLE));
        String hash = RequestFingerprint.of(TransactionType.MUA_VE, command.studentId(), command.vehicleId(),
                command.month());

        return transactions.inTransaction(() -> {
            Optional<MonthlyPass> existing = passes.findFor(command.vehicleId(), command.month());
            Optional<PointTransactionRepository.Recorded> sameKey = ledger.findByIdempotencyKey(command.idempotencyKey());
            if (existing.isPresent()) {
                MonthlyPass pass = existing.get();
                boolean replay = sameKey.isPresent() && sameKey.get().transaction().id() == pass.transactionId();
                if (!replay) {
                    throw new DomainException(ErrorCode.PASS_ALREADY_EXISTS);
                }
                long balance = wallets.findByUserId(command.studentId()).orElseThrow().balance();
                return new Purchase(pass, vehicle, balance, true);
            }
            if (sameKey.isPresent()) {
                throw new DomainException(ErrorCode.IDEMPOTENCY_KEY_REUSED);       // key đã dùng cho việc khác
            }
            long studentWalletId = wallets.findByUserId(command.studentId()).orElseThrow().id();
            long ownerWalletId = ownerWallet.receivingWalletId();
            Map<Long, Wallet> locked = wallets.lockInIdOrder(List.of(studentWalletId, ownerWalletId));
            Wallet student = locked.get(studentWalletId);
            Wallet owner = locked.get(ownerWalletId);
            Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            PassPurchase purchase = PassPurchase.execute(student, owner, price, command.studentId(),
                    command.idempotencyKey(), now);                              // không đủ điểm -> INSUFFICIENT_POINTS
            wallets.save(student);
            wallets.save(owner);
            long transactionId = ledger.save(purchase, hash);
            MonthlyPass pass = passes.insert(MonthlyPass.purchase(command.studentId(), command.vehicleId(),
                    command.month(), price, transactionId, now));
            events.publish(new PassPurchased(command.studentId(), new PlateNumber(vehicle.plateNumber()).display(),
                    pass.month(), price, pass.validUntil()));
            return new Purchase(pass, vehicle, purchase.sourceBalanceAfter(), false);
        });
    }
}
