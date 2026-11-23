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
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Đợt 5 — sinh viên mua vé tháng (1 tháng mặc định hoặc mua gộp 1–12 tháng liên tiếp, tính nguyên giá mỗi tháng).
 * MỘT transaction: kiểm tra không trùng bất kỳ tháng nào → khóa 2 ví → trừ 1 lần tổng tiền →
 * ghi từng tháng vào {@code monthly_passes} → phát sự kiện thông báo.
 */
public class BuyPassUseCase {

    public static final int MAX_MONTHS = 12;

    public record Command(long studentId, UUID idempotencyKey, long vehicleId, YearMonth month, int monthsCount) {
        public Command(long studentId, UUID idempotencyKey, long vehicleId, YearMonth month) {
            this(studentId, idempotencyKey, vehicleId, month, 1);
        }
    }

    public record Purchase(
            MonthlyPass pass,
            VehicleInfo vehicle,
            long balanceAfter,
            boolean replayed,
            int monthsCount,
            long totalPrice,
            LocalDate validUntil) {

        public Purchase(MonthlyPass pass, VehicleInfo vehicle, long balanceAfter, boolean replayed) {
            this(pass, vehicle, balanceAfter, replayed, 1, pass.pricePoints(), pass.validUntil());
        }
    }

    /** Một xe trong màn hình mua vé: giá gốc/tháng, vé tháng này/tháng sau, và hạn liên tiếp còn lại nếu đang có vé. */
    public record Option(
            VehicleInfo vehicle,
            Long price,
            MonthlyPass current,
            MonthlyPass next,
            LocalDate activeValidUntil,
            Integer remainingDays) {

        public Option(VehicleInfo vehicle, Long price, MonthlyPass current, MonthlyPass next) {
            this(vehicle, price, current, next, null, null);
        }
    }

    public record Overview(
            YearMonth currentMonth,
            YearMonth nextMonth,
            List<Option> options,
            List<Owned> passes) {}

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
        LocalDate today = LocalDate.now(clock);
        List<VehicleInfo> vehicles = catalog.vehiclesOf(studentId);
        List<MonthlyPass> allStudentPasses = passes.findByStudent(studentId);
        Map<Long, Set<YearMonth>> coveredByVehicle = allStudentPasses.stream()
                .collect(Collectors.groupingBy(
                        MonthlyPass::vehicleId,
                        Collectors.mapping(MonthlyPass::month, Collectors.toSet())));

        List<Option> options = vehicles.stream().filter(VehicleInfo::active)
                .map(v -> {
                    Set<YearMonth> covered = coveredByVehicle.getOrDefault(v.id(), Set.of());
                    LocalDate activeUntil = null;
                    Integer remainingDays = null;
                    if (covered.contains(current)) {
                        YearMonth cursor = current;
                        while (covered.contains(cursor.plusMonths(1))) {
                            cursor = cursor.plusMonths(1);
                        }
                        activeUntil = cursor.atEndOfMonth();
                        remainingDays = (int) ChronoUnit.DAYS.between(today, activeUntil) + 1;
                    }
                    return new Option(
                            v,
                            catalog.price(v.vehicleTypeCode()).orElse(null),
                            passes.findFor(v.id(), current).orElse(null),
                            passes.findFor(v.id(), current.plusMonths(1)).orElse(null),
                            activeUntil,
                            remainingDays);
                })
                .toList();
        Map<Long, VehicleInfo> byId = vehicles.stream().collect(Collectors.toMap(VehicleInfo::id, Function.identity()));
        List<Owned> owned = allStudentPasses.stream()
                .map(p -> new Owned(p, byId.get(p.vehicleId())))
                .toList();
        return new Overview(current, current.plusMonths(1), options, owned);
    }

    public Purchase buy(Command command) {
        if (command.monthsCount() < 1 || command.monthsCount() > MAX_MONTHS) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR);
        }
        MonthlyPass.requirePurchasable(command.month(), LocalDate.now(clock));
        VehicleInfo vehicle = catalog.vehiclesOf(command.studentId()).stream()
                .filter(v -> v.id() == command.vehicleId()).findFirst()
                .orElseThrow(() -> new DomainException(ErrorCode.VEHICLE_NOT_FOUND));
        if (!vehicle.active()) {
            throw new DomainException(ErrorCode.VEHICLE_INACTIVE);
        }
        long monthlyPrice = catalog.price(vehicle.vehicleTypeCode())
                .orElseThrow(() -> new DomainException(ErrorCode.PASS_NOT_AVAILABLE));

        List<YearMonth> months = IntStream.range(0, command.monthsCount())
                .mapToObj(command.month()::plusMonths)
                .toList();
        long totalPrice = Math.multiplyExact(monthlyPrice, command.monthsCount());
        String hash = command.monthsCount() == 1
                ? RequestFingerprint.of(TransactionType.MUA_VE, command.studentId(), command.vehicleId(), command.month())
                : RequestFingerprint.of(TransactionType.MUA_VE, command.studentId(), command.vehicleId(),
                        command.month(), command.monthsCount());

        return transactions.inTransaction(() -> {
            Optional<PointTransactionRepository.Recorded> sameKey = ledger.findByIdempotencyKey(command.idempotencyKey());
            if (sameKey.isPresent()) {
                PointTransactionRepository.Recorded recorded = sameKey.get();
                if (!recorded.requestHash().equals(hash)) {
                    throw new DomainException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
                }
                List<MonthlyPass> bundle = passes.findByTransactionId(recorded.transaction().id());
                if (bundle.isEmpty()) {
                    bundle = passes.findFor(command.vehicleId(), command.month()).stream()
                            .filter(p -> p.transactionId() == recorded.transaction().id())
                            .toList();
                }
                LocalDate bundleEnd = bundle.stream().map(MonthlyPass::validUntil).max(LocalDate::compareTo)
                        .orElseGet(() -> months.getLast().atEndOfMonth());
                MonthlyPass first = bundle.isEmpty()
                        ? MonthlyPass.purchase(command.studentId(), command.vehicleId(), command.month(), monthlyPrice,
                                recorded.transaction().id(), clock.instant())
                        : bundle.getFirst();
                long balance = wallets.findByUserId(command.studentId()).orElseThrow().balance();
                return new Purchase(first, vehicle, balance, true, months.size(),
                        recorded.transaction().points(), bundleEnd);
            }

            if (!passes.findForVehicleInMonths(command.vehicleId(), months).isEmpty()) {
                throw new DomainException(ErrorCode.PASS_ALREADY_EXISTS);
            }

            long studentWalletId = wallets.findByUserId(command.studentId()).orElseThrow().id();
            long ownerWalletId = ownerWallet.receivingWalletId();
            Map<Long, Wallet> locked = wallets.lockInIdOrder(List.of(studentWalletId, ownerWalletId));
            Wallet student = locked.get(studentWalletId);
            Wallet owner = locked.get(ownerWalletId);
            Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            PassPurchase purchase = PassPurchase.execute(student, owner, totalPrice, command.studentId(),
                    command.idempotencyKey(), now);
            wallets.save(student);
            wallets.save(owner);
            long transactionId = ledger.save(purchase, hash);

            MonthlyPass firstPass = null;
            for (YearMonth m : months) {
                MonthlyPass inserted = passes.insert(MonthlyPass.purchase(
                        command.studentId(), command.vehicleId(), m, monthlyPrice, transactionId, now));
                if (firstPass == null) {
                    firstPass = inserted;
                }
            }
            LocalDate validUntil = months.getLast().atEndOfMonth();
            events.publish(new PassPurchased(
                    command.studentId(),
                    new PlateNumber(vehicle.plateNumber()).display(),
                    command.month(),
                    totalPrice,
                    validUntil,
                    months.size()));
            return new Purchase(firstPass, vehicle, purchase.sourceBalanceAfter(), false,
                    months.size(), totalPrice, validUntil);
        });
    }
}
