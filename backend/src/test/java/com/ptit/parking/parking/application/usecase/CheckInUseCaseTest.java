package com.ptit.parking.parking.application.usecase;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import com.ptit.parking.catalog.domain.FeeRule;
import com.ptit.parking.parking.application.port.out.InMemoryParkingSessionRepository;
import com.ptit.parking.parking.application.port.out.InMemoryVehicleOwnerDirectory;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.VehicleOwner;
import com.ptit.parking.parking.application.usecase.CheckInUseCase.Command;
import com.ptit.parking.parking.application.usecase.CheckInUseCase.Result;
import com.ptit.parking.parking.domain.ParkingSession;
import com.ptit.parking.parking.domain.TimeSlotFeePolicy;
import com.ptit.parking.payment.application.port.out.InMemoryWalletRepository;
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.student.domain.PlateNumber;

/** BV01 — xác nhận cho xe vào: kiểm tra lại từ đầu, chỉ kiểm tra điểm (không trừ), bảo vệ cho vào lấy từ phiên. */
class CheckInUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-27T00:55:00Z");
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final long GUARD = 4L;
    private static final long VEHICLE = 100L;
    private static final long STUDENT = 3L;
    private static final VehicleOwner OWNER = new VehicleOwner(VEHICLE, new PlateNumber("29A12345"), "XE_MAY",
            STUDENT, "B21DCCN001", "Nguyễn Văn A");

    /** Nhật ký "việc gì xảy ra, có nằm trong transaction không" — kiểm tra thứ tự khóa → ghi. */
    private final List<String> log = new ArrayList<>();
    private boolean inTransaction;

    private final InMemoryVehicleOwnerDirectory vehicles = new InMemoryVehicleOwnerDirectory() {
        @Override
        public Optional<VehicleOwner> lockByPlate(PlateNumber plate) {
            log.add("khóa xe " + plate + (inTransaction ? " (trong transaction)" : " (NGOÀI transaction)"));
            return super.lockByPlate(plate);
        }
    };
    private final InMemoryParkingSessionRepository sessions = new InMemoryParkingSessionRepository() {
        @Override
        public synchronized ParkingSession save(ParkingSession session) {
            log.add("lưu lượt gửi" + (inTransaction ? " (trong transaction)" : " (NGOÀI transaction)"));
            return super.save(session);
        }
    };
    private final InMemoryWalletRepository wallets = new InMemoryWalletRepository();
    private final TimeSlotFeePolicy feePolicy = new TimeSlotFeePolicy(List.of(
            new FeeRule(LocalTime.MIDNIGHT, 3000), new FeeRule(LocalTime.of(18, 0), 5000)), VN);
    private final TransactionRunner transactions = new TransactionRunner() {
        @Override
        public <T> T inTransaction(Supplier<T> work) {
            inTransaction = true;
            try {
                return work.get();
            } finally {
                inTransaction = false;
            }
        }
    };

    private final CheckInUseCase checkIn = new CheckInUseCase(vehicles, sessions, wallets, feePolicy, transactions,
            Clock.fixed(NOW, VN));

    CheckInUseCaseTest() {
        vehicles.add(OWNER);
    }

    @Test
    void duDiem_taoLuotDangGui_baoVeTuPhien_khongTruDiem() {
        long wallet = wallets.insert(new Wallet(null, STUDENT, 3_000));    // đúng bằng mức thấp nhất vẫn được vào

        Result result = checkIn.checkIn(new Command(GUARD, "29a-123.45"));

        ParkingSession session = result.session();
        assertThat(session.id()).isNotNull();
        assertThat(session.isOpen()).isTrue();
        assertThat(session.vehicleId()).isEqualTo(VEHICLE);
        assertThat(session.entryGuardId()).isEqualTo(GUARD);
        assertThat(session.enteredAt()).isEqualTo(NOW);                    // giờ máy chủ, không phải giờ client gửi
        assertThat(result.vehicle()).isEqualTo(OWNER);
        assertThat(sessions.all()).hasSize(1);
        assertThat(wallets.balanceOf(wallet)).isEqualTo(3_000);            // R04: chỉ kiểm tra, KHÔNG trừ
    }

    @Test
    void khoaDongXeTruoc_roiMoiGhi_caHaiTrongMotTransaction() {
        wallets.insert(new Wallet(null, STUDENT, 10_000));

        checkIn.checkIn(new Command(GUARD, "29A12345"));

        assertThat(log).containsExactly(
                "khóa xe 29A12345 (trong transaction)",
                "lưu lượt gửi (trong transaction)");
    }

    @Test
    void thieuDiem_2999_khongChoVao_khongTaoLuot() {
        wallets.insert(new Wallet(null, STUDENT, 2_999));

        assertError(() -> checkIn.checkIn(new Command(GUARD, "29A12345")), ErrorCode.INSUFFICIENT_POINTS);
        assertThat(sessions.all()).isEmpty();
    }

    @Test
    void xeDangGui_khongChoVaoLanHai_R05() {
        wallets.insert(new Wallet(null, STUDENT, 10_000));
        checkIn.checkIn(new Command(GUARD, "29A12345"));

        assertError(() -> checkIn.checkIn(new Command(GUARD, "29A12345")), ErrorCode.SESSION_ALREADY_OPEN);
        assertThat(sessions.all()).hasSize(1);
    }

    @Test
    void xeDangGui_vaThieuDiem_baoLuotDangMoTruoc_cungThuTuVoiPreview() {
        wallets.insert(new Wallet(null, STUDENT, 0));
        sessions.markOpen(VEHICLE);

        assertError(() -> checkIn.checkIn(new Command(GUARD, "29A12345")), ErrorCode.SESSION_ALREADY_OPEN);
    }

    @Test
    void bienSoChuaDangKy_PLATE_NOT_FOUND() {
        assertError(() -> checkIn.checkIn(new Command(GUARD, "30B99999")), ErrorCode.PLATE_NOT_FOUND);
        assertThat(sessions.all()).isEmpty();
    }

    @Test
    void bienSoSaiDinhDang_VALIDATION_ERROR_khongDungToiCSDL() {
        assertError(() -> checkIn.checkIn(new Command(GUARD, "29-A1")), ErrorCode.VALIDATION_ERROR);
        assertThat(log).isEmpty();
    }

    private static void assertError(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(expected));
    }
}