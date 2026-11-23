package com.ptit.parking.parking.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.catalog.domain.FeeRule;
import com.ptit.parking.parking.application.port.out.GateCashRepository;
import com.ptit.parking.parking.application.port.out.InMemoryParkingSessionRepository;
import com.ptit.parking.parking.application.port.out.InMemoryVehicleOwnerDirectory;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.VehicleOwner;
import com.ptit.parking.parking.application.usecase.CheckOutByCashUseCase.Command;
import com.ptit.parking.parking.domain.FeeQuote;
import com.ptit.parking.parking.domain.GateCashCollection;
import com.ptit.parking.parking.domain.OvernightAwareFeePolicy;
import com.ptit.parking.parking.domain.ParkingPolicyConfig;
import com.ptit.parking.parking.domain.ParkingSession;
import com.ptit.parking.parking.domain.TimeSlotFeePolicy;
import com.ptit.parking.payment.domain.ExchangeRate;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.student.domain.PlateNumber;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class CheckOutByCashUseCaseTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final long GUARD = 4L;
    private static final long SHIFT = 12L;
    private static final long VEHICLE = 100L;
    private static final long STUDENT = 3L;
    private static final VehicleOwner OWNER = new VehicleOwner(
            VEHICLE, new PlateNumber("29A12345"), "XE_MAY", STUDENT, "B21DCCN001", "Nguyễn Văn A");

    private final InMemoryVehicleOwnerDirectory vehicles = new InMemoryVehicleOwnerDirectory();
    private final InMemoryParkingSessionRepository sessions = new InMemoryParkingSessionRepository();
    private final InMemoryGateCashRepository gateCash = new InMemoryGateCashRepository();
    private final TransactionRunner tx = new TransactionRunner() {
        @Override
        public <T> T inTransaction(Supplier<T> work) { return work.get(); }
    };
    private final TimeSlotFeePolicy basePolicy = new TimeSlotFeePolicy(List.of(
            new FeeRule(LocalTime.MIDNIGHT, 3_000),
            new FeeRule(LocalTime.of(18, 0), 5_000)), VN);
    private ParkingPolicyConfig policyConfig = ParkingPolicyConfig.DEFAULT;
    private final OvernightAwareFeePolicy feePolicy = new OvernightAwareFeePolicy(basePolicy, () -> policyConfig, VN);

    CheckOutByCashUseCaseTest() {
        vehicles.add(OWNER);
    }

    private CheckOutByCashUseCase at(Instant now) {
        return new CheckOutByCashUseCase(
                sessions, vehicles, gateCash, guard -> Optional.of(SHIFT),
                () -> new ExchangeRate(1), feePolicy, tx, Clock.fixed(now, VN));
    }

    @Test
    void thuTienMatTrongNgay_dongLuotVaGhiBangKeCa() {
        Instant entered = ZonedDateTime.of(2026, 11, 22, 8, 0, 0, 0, VN).toInstant();
        Instant exit = ZonedDateTime.of(2026, 11, 22, 18, 30, 0, 0, VN).toInstant();
        ParkingSession open = sessions.markOpen(VEHICLE, entered);
        UUID key = UUID.randomUUID();

        var result = at(exit).checkOutByCash(new Command(GUARD, key, open.id()));

        assertThat(result.replayed()).isFalse();
        assertThat(result.collection().amount()).isEqualTo(5_000);
        assertThat(result.collection().overnight()).isFalse();
        assertThat(result.collection().shiftId()).isEqualTo(SHIFT);
        assertThat(sessions.findById(open.id()).orElseThrow().isOpen()).isFalse();
    }

    @Test
    void thuTienMatQuaDem_tinh15000Dong() {
        Instant entered = ZonedDateTime.of(2026, 11, 22, 17, 0, 0, 0, VN).toInstant();
        Instant exitNextDay = ZonedDateTime.of(2026, 11, 23, 8, 10, 0, 0, VN).toInstant();
        ParkingSession open = sessions.markOpen(VEHICLE, entered);

        var result = at(exitNextDay).checkOutByCash(new Command(GUARD, UUID.randomUUID(), open.id()));

        assertThat(result.collection().amount()).isEqualTo(15_000);
        assertThat(result.collection().overnight()).isTrue();
    }

    @Test
    void bamLaiCungKey_traKetQuaCuKhongThuHaiLan() {
        Instant entered = ZonedDateTime.of(2026, 11, 22, 8, 0, 0, 0, VN).toInstant();
        Instant exit = ZonedDateTime.of(2026, 11, 22, 17, 0, 0, 0, VN).toInstant();
        ParkingSession open = sessions.markOpen(VEHICLE, entered);
        UUID key = UUID.randomUUID();

        var first = at(exit).checkOutByCash(new Command(GUARD, key, open.id()));
        var second = at(exit).checkOutByCash(new Command(GUARD, key, open.id()));

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.collection().id()).isEqualTo(first.collection().id());
        assertThat(gateCash.count()).isEqualTo(1);
    }

    @Test
    void qua20h_chanThuTienMatVaGiuLuotDangGui() {
        Instant entered = ZonedDateTime.of(2026, 11, 22, 8, 0, 0, 0, VN).toInstant();
        Instant at2015 = ZonedDateTime.of(2026, 11, 22, 20, 15, 0, 0, VN).toInstant();
        ParkingSession open = sessions.markOpen(VEHICLE, entered);

        assertThatThrownBy(() -> at(at2015).checkOutByCash(new Command(GUARD, UUID.randomUUID(), open.id())))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).errorCode())
                .isEqualTo(ErrorCode.CHECKOUT_CURFEW);
        assertThat(sessions.findById(open.id()).orElseThrow().isOpen()).isTrue();
        assertThat(gateCash.count()).isZero();
    }

    @Test
    void veThangMienPhi_khongThuTienMat() {
        Instant entered = ZonedDateTime.of(2026, 11, 22, 8, 0, 0, 0, VN).toInstant();
        Instant exit = ZonedDateTime.of(2026, 11, 22, 17, 0, 0, 0, VN).toInstant();
        ParkingSession open = sessions.markOpen(VEHICLE, entered);
        var freePolicy = new com.ptit.parking.parking.domain.FeePolicy() {
            @Override public long feeAt(ZonedDateTime exitTime) { return 3_000; }
            @Override public long minimumFee() { return 3_000; }
            @Override public FeeQuote quote(long vehicleId, ZonedDateTime exitTime) {
                return FeeQuote.coveredByPass(LocalDate.of(2026, 11, 30));
            }
        };
        var uc = new CheckOutByCashUseCase(sessions, vehicles, gateCash, guard -> Optional.of(SHIFT),
                () -> new ExchangeRate(1), freePolicy, tx, Clock.fixed(exit, VN));

        assertThatThrownBy(() -> uc.checkOutByCash(new Command(GUARD, UUID.randomUUID(), open.id())))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).errorCode())
                .isEqualTo(ErrorCode.INVALID_AMOUNT);
    }

    private static class InMemoryGateCashRepository implements GateCashRepository {
        private final Map<UUID, GateCashCollection> byKey = new LinkedHashMap<>();
        private long nextId = 1;

        @Override
        public Optional<GateCashCollection> findByIdempotencyKey(UUID key) {
            return Optional.ofNullable(byKey.get(key));
        }

        @Override
        public GateCashCollection save(GateCashCollection collection) {
            GateCashCollection saved = collection.withId(nextId++);
            byKey.put(saved.idempotencyKey(), saved);
            return saved;
        }

        @Override
        public List<CashEntry> findByGuardSince(long guardId, Instant from) { return List.of(); }

        @Override
        public List<CashEntry> findAllBetween(Instant from, Instant to) { return List.of(); }

        int count() { return byKey.size(); }
    }
}
