package com.ptit.parking.parking.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.catalog.domain.FeeRule;
import com.ptit.parking.parking.application.port.out.InMemoryParkingSessionRepository;
import com.ptit.parking.parking.application.port.out.InMemoryVehicleOwnerDirectory;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.VehicleOwner;
import com.ptit.parking.parking.application.usecase.PayParkingFeeByVietQrUseCase.Command;
import com.ptit.parking.parking.application.usecase.PayParkingFeeByVietQrUseCase.Result;
import com.ptit.parking.parking.domain.ParkingSession;
import com.ptit.parking.parking.domain.TimeSlotFeePolicy;
import com.ptit.parking.payment.application.IdempotencyGuard;
import com.ptit.parking.payment.application.port.out.InMemoryPointTransactionRepository;
import com.ptit.parking.payment.application.port.out.InMemoryWalletRepository;
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
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử đơn vị cho {@link PayParkingFeeByVietQrUseCase} (BV02-VietQR — thanh toán chuyển khoản trực tiếp).
 */
class PayParkingFeeByVietQrUseCaseTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant ENTERED = Instant.parse("2026-10-05T00:55:00Z");        // 07:55 giờ VN
    private static final Instant AT_18_30 = Instant.parse("2026-10-05T11:30:00Z");       // 18:30 giờ VN -> 5.000
    private static final long GUARD = 4L;
    private static final long VEHICLE = 100L;
    private static final long STUDENT = 3L;
    private static final long OWNER_USER = 2L;
    private static final VehicleOwner OWNER = new VehicleOwner(VEHICLE, new PlateNumber("29A12345"), "XE_MAY",
            STUDENT, "B21DCCN001", "Nguyễn Văn A");

    private final InMemoryVehicleOwnerDirectory vehicles = new InMemoryVehicleOwnerDirectory();
    private final InMemoryParkingSessionRepository sessions = new InMemoryParkingSessionRepository();
    private final InMemoryWalletRepository wallets = new InMemoryWalletRepository();
    private final InMemoryPointTransactionRepository transactions = new InMemoryPointTransactionRepository();
    private final TransactionRunner transactionRunner = new TransactionRunner() {
        @Override
        public <T> T inTransaction(Supplier<T> work) {
            return work.get();
        }
    };
    private final TimeSlotFeePolicy feePolicy = new TimeSlotFeePolicy(List.of(
            new FeeRule(LocalTime.MIDNIGHT, 3000), new FeeRule(LocalTime.of(18, 0), 5000)), VN);

    private final long receiverWallet = wallets.insert(new Wallet(null, OWNER_USER, 100));
    private final long studentWallet;
    private final ParkingSession open;

    PayParkingFeeByVietQrUseCaseTest() {
        vehicles.add(OWNER);
        // Sinh viên chỉ có 500 điểm (thiếu điểm vì phí là 5.000)
        studentWallet = wallets.insert(new Wallet(null, STUDENT, 500));
        open = sessions.markOpen(VEHICLE, ENTERED);
    }

    private PayParkingFeeByVietQrUseCase at(Instant now) {
        return new PayParkingFeeByVietQrUseCase(sessions, vehicles, wallets, () -> receiverWallet,
                new IdempotencyGuard(transactions, transactionRunner), feePolicy, Clock.fixed(now, VN));
    }

    @Test
    void xacNhanVietQr_dongLuot_congDoiUngSV_vaChuyenViNhan() {
        UUID key = UUID.randomUUID();

        Result result = at(AT_18_30).payByVietQr(new Command(GUARD, key, open.id()));

        ParkingPayment payment = result.payment();
        assertThat(result.replayed()).isFalse();
        assertThat(payment.id()).isNotNull();
        assertThat(payment.sessionId()).isEqualTo(open.id());
        assertThat(payment.points()).isEqualTo(5_000);
        assertThat(payment.sourceWalletId()).isEqualTo(studentWallet);
        assertThat(payment.targetWalletId()).isEqualTo(receiverWallet);
        assertThat(payment.sourceBalanceAfter()).isEqualTo(500); // 500 ban đầu + 5.000 nạp VietQR - 5.000 phí = 500
        assertThat(payment.performedBy()).isEqualTo(GUARD);
        assertThat(payment.idempotencyKey()).isEqualTo(key);

        assertThat(wallets.balanceOf(studentWallet)).isEqualTo(500);
        assertThat(wallets.balanceOf(receiverWallet)).isEqualTo(5_100);

        ParkingSession closed = sessions.all().get(0);
        assertThat(closed.isOpen()).isFalse();
        assertThat(closed.exitedAt()).isEqualTo(AT_18_30);
    }

    @Test
    void idempotent_guiLaiCungKey_traLaiGiaoDichCu_khongCongTruHaiLan() {
        UUID key = UUID.randomUUID();
        PayParkingFeeByVietQrUseCase useCase = at(AT_18_30);

        Result first = useCase.payByVietQr(new Command(GUARD, key, open.id()));
        Result second = useCase.payByVietQr(new Command(GUARD, key, open.id()));

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.payment().id()).isEqualTo(first.payment().id());
        assertThat(wallets.balanceOf(studentWallet)).isEqualTo(500);
        assertThat(wallets.balanceOf(receiverWallet)).isEqualTo(5_100);
    }

    @Test
    void luotKhongTonTai_nemNO_OPEN_SESSION() {
        UUID key = UUID.randomUUID();

        assertThatThrownBy(() -> at(AT_18_30).payByVietQr(new Command(GUARD, key, 999999L)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).errorCode())
                .isEqualTo(ErrorCode.NO_OPEN_SESSION);
    }

    @Test
    void luotDaRa_nemSESSION_ALREADY_PAID() {
        UUID key = UUID.randomUUID();
        at(AT_18_30).payByVietQr(new Command(GUARD, key, open.id()));

        // Gửi lượt đã đóng nhưng với key mới -> SESSION_ALREADY_PAID
        UUID newKey = UUID.randomUUID();
        assertThatThrownBy(() -> at(AT_18_30).payByVietQr(new Command(GUARD, newKey, open.id())))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).errorCode())
                .isEqualTo(ErrorCode.SESSION_ALREADY_PAID);
    }
}

