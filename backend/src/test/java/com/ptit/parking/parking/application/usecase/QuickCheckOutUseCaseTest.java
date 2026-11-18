package com.ptit.parking.parking.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.catalog.domain.FeeRule;
import com.ptit.parking.parking.application.port.out.InMemoryParkingSessionRepository;
import com.ptit.parking.parking.application.port.out.InMemoryVehicleOwnerDirectory;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.VehicleOwner;
import com.ptit.parking.parking.application.usecase.QuickCheckOutUseCase.Command;
import com.ptit.parking.parking.application.usecase.QuickCheckOutUseCase.Result;
import com.ptit.parking.parking.domain.ParkingSession;
import com.ptit.parking.parking.domain.TimeSlotFeePolicy;
import com.ptit.parking.payment.application.IdempotencyGuard;
import com.ptit.parking.payment.application.port.out.InMemoryPointTransactionRepository;
import com.ptit.parking.payment.application.port.out.InMemoryWalletRepository;
import com.ptit.parking.payment.application.usecase.PayParkingFeeUseCase;
import com.ptit.parking.payment.domain.ParkingPayment;
import com.ptit.parking.payment.domain.PointTransaction;
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.student.domain.PlateNumber;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử đơn vị cho {@link QuickCheckOutUseCase} (BV02-Quick — thanh toán 1 chạm).
 */
class QuickCheckOutUseCaseTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant ENTERED = Instant.parse("2026-10-05T00:55:00Z");        // 07:55 giờ VN
    private static final Instant AT_18_30 = Instant.parse("2026-10-05T11:30:00Z");       // 18:30 giờ VN -> 5.000
    private static final long GUARD = 4L;
    private static final long VEHICLE = 100L;
    private static final long STUDENT = 3L;
    private static final long OWNER_USER = 2L;
    private static final VehicleOwner OWNER = new VehicleOwner(VEHICLE, new PlateNumber("29A12345"), "XE_MAY",
            STUDENT, "B21DCCN001", "Nguyễn Văn A");

    private final List<String> log = new ArrayList<>();
    private final InMemoryVehicleOwnerDirectory vehicles = new InMemoryVehicleOwnerDirectory();
    private final InMemoryParkingSessionRepository sessions = new InMemoryParkingSessionRepository() {
        @Override
        public synchronized void saveExit(ParkingSession session) {
            log.add("đóng lượt " + session.id());
            super.saveExit(session);
        }
    };
    private final InMemoryWalletRepository wallets = new InMemoryWalletRepository() {
        @Override
        public synchronized void save(Wallet wallet) {
            log.add("lưu ví " + wallet.id());
            super.save(wallet);
        }
    };
    private final InMemoryPointTransactionRepository transactions = new InMemoryPointTransactionRepository() {
        @Override
        public synchronized long save(PointTransaction transaction, String requestHash) {
            log.add("lưu giao dịch " + transaction.type());
            return super.save(transaction, requestHash);
        }
    };
    private final TransactionRunner transactionRunner = new TransactionRunner() {
        @Override
        public <T> T inTransaction(Supplier<T> work) {
            log.add("BEGIN");
            try {
                return work.get();
            } finally {
                log.add("END");
            }
        }
    };
    private final TimeSlotFeePolicy feePolicy = new TimeSlotFeePolicy(List.of(
            new FeeRule(LocalTime.MIDNIGHT, 3000), new FeeRule(LocalTime.of(18, 0), 5000)), VN);

    private final long receiverWallet = wallets.insert(new Wallet(null, OWNER_USER, 100));
    private final long studentWallet;
    private final ParkingSession open;

    QuickCheckOutUseCaseTest() {
        vehicles.add(OWNER);
        studentWallet = wallets.insert(new Wallet(null, STUDENT, 10_000));
        open = sessions.markOpen(VEHICLE, ENTERED);
    }

    private QuickCheckOutUseCase at(Instant now) {
        return new QuickCheckOutUseCase(sessions, vehicles, new PayParkingFeeUseCase(wallets, () -> receiverWallet),
                new IdempotencyGuard(transactions, transactionRunner), feePolicy, Clock.fixed(now, VN));
    }

    @Test
    void xacNhanRa1Cham_truPhi_congViNhan_dongLuot_traDuThongTinChuXe() {
        UUID key = UUID.randomUUID();

        Result result = at(AT_18_30).checkOut(new Command(GUARD, key, "29a-123.45"));

        ParkingPayment payment = result.payment();
        assertThat(result.replayed()).isFalse();
        assertThat(payment.id()).isNotNull();
        assertThat(payment.sessionId()).isEqualTo(open.id());
        assertThat(payment.points()).isEqualTo(5_000);
        assertThat(payment.sourceWalletId()).isEqualTo(studentWallet);
        assertThat(payment.targetWalletId()).isEqualTo(receiverWallet);
        assertThat(payment.sourceBalanceAfter()).isEqualTo(5_000);
        assertThat(payment.performedBy()).isEqualTo(GUARD);
        assertThat(payment.idempotencyKey()).isEqualTo(key);
        assertThat(payment.createdAt()).isEqualTo(AT_18_30);

        assertThat(result.vehicleOwner()).isNotNull();
        assertThat(result.vehicleOwner().plate().value()).isEqualTo("29A12345");
        assertThat(result.vehicleOwner().studentCode()).isEqualTo("B21DCCN001");
        assertThat(result.vehicleOwner().fullName()).isEqualTo("Nguyễn Văn A");

        assertThat(wallets.balanceOf(studentWallet)).isEqualTo(5_000);
        assertThat(wallets.balanceOf(receiverWallet)).isEqualTo(5_100);
        ParkingSession closed = sessions.all().get(0);
        assertThat(closed.isOpen()).isFalse();
        assertThat(closed.exitedAt()).isEqualTo(AT_18_30);
    }

    @Test
    void idempotent_guiLaiCungKey_traLaiKetQuaCu_khongTruHaiLan() {
        UUID key = UUID.randomUUID();
        QuickCheckOutUseCase useCase = at(AT_18_30);

        Result first = useCase.checkOut(new Command(GUARD, key, "29A12345"));
        Result second = useCase.checkOut(new Command(GUARD, key, "29A12345"));

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.payment().id()).isEqualTo(first.payment().id());
        assertThat(wallets.balanceOf(studentWallet)).isEqualTo(5_000);
        assertThat(wallets.balanceOf(receiverWallet)).isEqualTo(5_100);
    }

    @Test
    void bienSoChuaDangKy_nemPLATE_NOT_FOUND() {
        UUID key = UUID.randomUUID();

        assertThatThrownBy(() -> at(AT_18_30).checkOut(new Command(GUARD, key, "99A99999")))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).errorCode())
                .isEqualTo(ErrorCode.PLATE_NOT_FOUND);
    }

    @Test
    void khongCoLuotDangMo_nemNO_OPEN_SESSION() {
        UUID key = UUID.randomUUID();
        sessions.all().clear(); // Không có lượt gửi nào

        assertThatThrownBy(() -> at(AT_18_30).checkOut(new Command(GUARD, key, "29A12345")))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).errorCode())
                .isEqualTo(ErrorCode.NO_OPEN_SESSION);
    }

    @Test
    void khongDuDiem_nemINSUFFICIENT_POINTS_vaRollbackToanBo() {
        UUID key = UUID.randomUUID();
        // Ví sinh viên chỉ có 2.000 điểm, phí là 5.000
        wallets.save(new Wallet(studentWallet, STUDENT, 2_000));

        assertThatThrownBy(() -> at(AT_18_30).checkOut(new Command(GUARD, key, "29A12345")))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).errorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_POINTS);

        // Số dư không đổi, lượt gửi vẫn DANG_GUI
        assertThat(wallets.balanceOf(studentWallet)).isEqualTo(2_000);
        assertThat(wallets.balanceOf(receiverWallet)).isEqualTo(100);
        assertThat(sessions.all().get(0).isOpen()).isTrue();
    }

    @Test
    void bienSoSaiDinhDang_nemVALIDATION_ERROR() {
        UUID key = UUID.randomUUID();

        assertThatThrownBy(() -> at(AT_18_30).checkOut(new Command(GUARD, key, "abc")))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
}

