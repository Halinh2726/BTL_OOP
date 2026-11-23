package com.ptit.parking.parking.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.ptit.parking.catalog.domain.FeeRule;
import com.ptit.parking.parking.application.event.LowBalanceWarning;
import com.ptit.parking.parking.application.event.VehicleCheckedIn;
import com.ptit.parking.parking.application.event.VehicleCheckedOut;
import com.ptit.parking.parking.application.port.out.InMemoryParkingSessionRepository;
import com.ptit.parking.parking.application.port.out.InMemoryVehicleOwnerDirectory;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.VehicleOwner;
import com.ptit.parking.parking.domain.FeePolicy;
import com.ptit.parking.parking.domain.FeeQuote;
import com.ptit.parking.parking.domain.ParkingSession;
import com.ptit.parking.parking.domain.TimeSlotFeePolicy;
import com.ptit.parking.payment.application.IdempotencyGuard;
import com.ptit.parking.payment.application.port.out.InMemoryPointTransactionRepository;
import com.ptit.parking.payment.application.port.out.InMemoryWalletRepository;
import com.ptit.parking.payment.application.usecase.PayParkingFeeUseCase;
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.application.event.NotifiableEvent;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.student.domain.PlateNumber;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** Kiểm tra sự kiện thông báo xe vào, xe ra, trừ điểm và cảnh báo sớm khi sắp hết điểm. */
class ParkingNotificationsTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant CHECK_IN_AT = Instant.parse("2026-10-05T01:00:00Z");
    private static final Instant CHECK_OUT_AT = Instant.parse("2026-10-05T03:00:00Z"); // giờ VN 10:00 -> phí 3.000
    private static final long GUARD = 4L;
    private static final long STUDENT = 3L;
    private static final long OWNER = 2L;
    private static final long VEHICLE = 100L;
    private static final VehicleOwner CAR = new VehicleOwner(
            VEHICLE, new PlateNumber("29A12345"), "XE_MAY", STUDENT, "B21DCCN001", "Nguyễn Văn A");

    private final TransactionRunner directly = new TransactionRunner() {
        @Override
        public <T> T inTransaction(Supplier<T> work) {
            return work.get();
        }
    };
    private final FeePolicy standardFee = new TimeSlotFeePolicy(
            List.of(new FeeRule(LocalTime.MIDNIGHT, 3_000), new FeeRule(LocalTime.of(18, 0), 5_000)), VN);

    @Test
    void xeVaoBai_phatThongBaoXeVao() {
        InMemoryVehicleOwnerDirectory vehicles = new InMemoryVehicleOwnerDirectory();
        vehicles.add(CAR);
        InMemoryParkingSessionRepository sessions = new InMemoryParkingSessionRepository();
        InMemoryWalletRepository wallets = new InMemoryWalletRepository();
        wallets.insert(new Wallet(null, STUDENT, 10_000));
        List<Object> published = new ArrayList<>();

        CheckInUseCase checkIn = new CheckInUseCase(vehicles, sessions, wallets, standardFee, directly,
                published::add, Clock.fixed(CHECK_IN_AT, VN));
        checkIn.checkIn(new CheckInUseCase.Command(GUARD, "29A12345"));

        assertThat(published).singleElement().isInstanceOf(VehicleCheckedIn.class);
        NotifiableEvent.Notice notice = ((VehicleCheckedIn) published.get(0)).notices().get(0);
        assertThat(notice.to()).isEqualTo(new NotifiableEvent.User(STUDENT));
        assertThat(notice.kind()).isEqualTo("XE_VAO");
        assertThat(notice.title()).contains("29A-123.45");
    }

    @Test
    void xeRaCoVeThang_phatThongBaoXeRaMienPhi_khongCanhBaoSoDuThap() {
        InMemoryVehicleOwnerDirectory vehicles = new InMemoryVehicleOwnerDirectory();
        vehicles.add(CAR);
        InMemoryParkingSessionRepository sessions = new InMemoryParkingSessionRepository();
        ParkingSession open = sessions.markOpen(VEHICLE, CHECK_IN_AT);
        InMemoryWalletRepository wallets = new InMemoryWalletRepository();
        long receiver = wallets.insert(new Wallet(null, OWNER, 0));
        wallets.insert(new Wallet(null, STUDENT, 500));
        List<Object> published = new ArrayList<>();

        FeePolicy passFee = new FeePolicy() {
            @Override
            public long feeAt(ZonedDateTime exitTime) { return standardFee.feeAt(exitTime); }

            @Override
            public long minimumFee() { return standardFee.minimumFee(); }

            @Override
            public FeeQuote quote(long vehicleId, ZonedDateTime exitTime) {
                return FeeQuote.coveredByPass(LocalDate.of(2026, 10, 31));
            }

            @Override
            public long minimumFeeFor(long vehicleId, ZonedDateTime at) { return 0; }
        };

        CheckOutUseCase checkOut = new CheckOutUseCase(sessions, vehicles,
                new PayParkingFeeUseCase(wallets, () -> receiver),
                new IdempotencyGuard(new InMemoryPointTransactionRepository(), directly),
                passFee, wallets, directly, published::add, Clock.fixed(CHECK_OUT_AT, VN));

        CheckOutUseCase.Result result = checkOut.checkOut(
                new CheckOutUseCase.Command(GUARD, UUID.randomUUID(), open.id()));
        assertThat(result.freeExit()).isNotNull();
        assertThat(published).singleElement().isInstanceOf(VehicleCheckedOut.class);
        NotifiableEvent.Notice notice = ((VehicleCheckedOut) published.get(0)).notices().get(0);
        assertThat(notice.kind()).isEqualTo("XE_RA");
        assertThat(notice.title()).contains("vé tháng");
    }

    @Test
    void xeRaTruPhi_conDuoi2LuotToiThieu_canhBaoSom_conDuoi1Luot_baoKhongDu() {
        assertCheckoutEvents(7_000, List.of("XE_RA", "SO_DU_THAP"), "sắp hết");
        assertCheckoutEvents(4_000, List.of("XE_RA", "SO_DU_THAP"), "không đủ");
        assertCheckoutEvents(9_000, List.of("XE_RA"), null);
    }

    private void assertCheckoutEvents(long initialBalance, List<String> expectedKinds, String expectedLowTitleSnippet) {
        InMemoryVehicleOwnerDirectory vehicles = new InMemoryVehicleOwnerDirectory();
        vehicles.add(CAR);
        InMemoryParkingSessionRepository sessions = new InMemoryParkingSessionRepository();
        ParkingSession open = sessions.markOpen(VEHICLE, CHECK_IN_AT);
        InMemoryWalletRepository wallets = new InMemoryWalletRepository();
        long receiver = wallets.insert(new Wallet(null, OWNER, 0));
        wallets.insert(new Wallet(null, STUDENT, initialBalance));
        List<Object> published = new ArrayList<>();

        CheckOutUseCase checkOut = new CheckOutUseCase(sessions, vehicles,
                new PayParkingFeeUseCase(wallets, () -> receiver),
                new IdempotencyGuard(new InMemoryPointTransactionRepository(), directly),
                standardFee, wallets, directly, published::add, Clock.fixed(CHECK_OUT_AT, VN));

        checkOut.checkOut(new CheckOutUseCase.Command(GUARD, UUID.randomUUID(), open.id()));

        List<NotifiableEvent.Notice> notices = published.stream()
                .map(NotifiableEvent.class::cast)
                .flatMap(e -> e.notices().stream())
                .toList();
        assertThat(notices).extracting(NotifiableEvent.Notice::kind).containsExactlyElementsOf(expectedKinds);
        if (expectedLowTitleSnippet != null) {
            assertThat(published.get(1)).isInstanceOf(LowBalanceWarning.class);
            assertThat(notices.get(1).title()).contains(expectedLowTitleSnippet);
        }
    }
}
