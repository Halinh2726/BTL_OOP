package com.ptit.parking.parking.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.catalog.domain.FeeRule;
import com.ptit.parking.parking.application.port.out.InMemoryParkingSessionRepository;
import com.ptit.parking.parking.application.port.out.InMemoryVehicleOwnerDirectory;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.VehicleOwner;
import com.ptit.parking.parking.application.usecase.CheckOutUseCase.Command;
import com.ptit.parking.parking.application.usecase.CheckOutUseCase.Result;
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
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/**
 * BV02 «include» HT01 — xác nhận xe ra: trừ phí đúng một lần (M3-06), lỗi thì không gì thay đổi (M3-05),
 * phí tính lại lúc xác nhận, ví nhận từ cấu hình (R06).
 */
class CheckOutUseCaseTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant ENTERED = Instant.parse("2026-10-05T00:55:00Z");        // 07:55 giờ VN
    private static final Instant AT_18_30 = Instant.parse("2026-10-05T11:30:00Z");       // 18:30 giờ VN -> 5.000
    private static final Instant AT_17_59 = Instant.parse("2026-10-05T10:59:00Z");       // 17:59 giờ VN -> 3.000
    private static final long GUARD = 4L;
    private static final long VEHICLE = 100L;
    private static final long STUDENT = 3L;
    private static final long OWNER_USER = 2L;
    private static final VehicleOwner OWNER = new VehicleOwner(VEHICLE, new PlateNumber("29A12345"), "XE_MAY",
            STUDENT, "B21DCCN001", "Nguyễn Văn A");

    /** Nhật ký mọi thao tác ghi, kèm "có nằm trong transaction không" — kiểm tra M3-05. */
    private final List<String> log = new ArrayList<>();
    private boolean inTransaction;

    private final InMemoryVehicleOwnerDirectory vehicles = new InMemoryVehicleOwnerDirectory();
    private final InMemoryParkingSessionRepository sessions = new InMemoryParkingSessionRepository() {
        @Override
        public synchronized Optional<ParkingSession> lockById(long sessionId) {
            log.add("khóa lượt " + sessionId + where());
            return super.lockById(sessionId);
        }

        @Override
        public synchronized void saveExit(ParkingSession session) {
            log.add("đóng lượt " + session.id() + where());
            super.saveExit(session);
        }
    };
    private final InMemoryWalletRepository wallets = new InMemoryWalletRepository() {
        @Override
        public synchronized void save(Wallet wallet) {
            log.add("lưu ví " + wallet.id() + where());
            super.save(wallet);
        }
    };
    private final InMemoryPointTransactionRepository transactions = new InMemoryPointTransactionRepository() {
        @Override
        public synchronized long save(PointTransaction transaction, String requestHash) {
            log.add("lưu giao dịch " + transaction.type() + where());
            return super.save(transaction, requestHash);
        }
    };
    private final TransactionRunner transactionRunner = new TransactionRunner() {
        @Override
        public <T> T inTransaction(Supplier<T> work) {
            log.add("BEGIN");
            inTransaction = true;
            try {
                return work.get();
            } finally {
                inTransaction = false;
                log.add("END");
            }
        }
    };
    private final TimeSlotFeePolicy feePolicy = new TimeSlotFeePolicy(List.of(
            new FeeRule(LocalTime.MIDNIGHT, 3000), new FeeRule(LocalTime.of(18, 0), 5000)), VN);

    // Ví nhận tạo trước (id nhỏ hơn) — chủ tài khoản đã có 100 điểm
    private final long receiverWallet = wallets.insert(new Wallet(null, OWNER_USER, 100));
    private final long studentWallet;
    private final ParkingSession open;

    CheckOutUseCaseTest() {
        vehicles.add(OWNER);
        studentWallet = wallets.insert(new Wallet(null, STUDENT, 10_000));
        open = sessions.markOpen(VEHICLE, ENTERED);
    }

    private CheckOutUseCase at(Instant now) {
        return new CheckOutUseCase(sessions, vehicles, new PayParkingFeeUseCase(wallets, () -> receiverWallet),
                new IdempotencyGuard(transactions, transactionRunner), feePolicy, Clock.fixed(now, VN));
    }

    @Test
    void xacNhanRa_truPhi_congViNhan_dongLuot_luuGiaoDich() {
        UUID key = UUID.randomUUID();

        Result result = at(AT_18_30).checkOut(new Command(GUARD, key, open.id()));

        ParkingPayment payment = result.payment();
        assertThat(result.replayed()).isFalse();
        assertThat(payment.id()).isNotNull();                                   // giao dịch ĐÃ LƯU
        assertThat(payment.sessionId()).isEqualTo(open.id());
        assertThat(payment.points()).isEqualTo(5_000);                          // ra lúc 18:30
        assertThat(payment.sourceWalletId()).isEqualTo(studentWallet);
        assertThat(payment.targetWalletId()).isEqualTo(receiverWallet);          // R06
        assertThat(payment.sourceBalanceAfter()).isEqualTo(5_000);
        assertThat(payment.performedBy()).isEqualTo(GUARD);
        assertThat(payment.idempotencyKey()).isEqualTo(key);
        assertThat(payment.createdAt()).isEqualTo(AT_18_30);

        assertThat(wallets.balanceOf(studentWallet)).isEqualTo(5_000);
        assertThat(wallets.balanceOf(receiverWallet)).isEqualTo(5_100);
        ParkingSession closed = sessions.all().get(0);
        assertThat(closed.isOpen()).isFalse();
        assertThat(closed.exitedAt()).isEqualTo(AT_18_30);                      // giờ ra = giờ thanh toán
        assertThat(closed.exitGuardId()).isEqualTo(GUARD);
        assertThat(transactions.all()).hasSize(1);
    }

    @Test
    void phiTinhLaiTheoGioXacNhan_R03() {
        Result result = at(AT_17_59).checkOut(new Command(GUARD, UUID.randomUUID(), open.id()));

        assertThat(result.payment().points()).isEqualTo(3_000);
        assertThat(wallets.balanceOf(studentWallet)).isEqualTo(7_000);
    }

    @Test
    void moiThaoTacGhi_namTrongMotTransaction_khoaLuotTruoc_M3_05() {
        at(AT_18_30).checkOut(new Command(GUARD, UUID.randomUUID(), open.id()));

        assertThat(log).containsExactly(
                "BEGIN",
                "khóa lượt 1 (trong transaction)",
                "lưu ví 2 (trong transaction)",                                // ví sinh viên
                "lưu ví 1 (trong transaction)",                                // ví nhận
                "đóng lượt 1 (trong transaction)",
                "lưu giao dịch THANH_TOAN (trong transaction)",
                "END");
    }

    @Test
    void thieuDiem_khongGiThayDoi_haiViVaLuotGiuNguyen_M3_05() {
        Wallet poor = wallets.lockById(studentWallet);
        poor.debit(5_001);                                                      // còn 4.999 < 5.000
        wallets.save(poor);
        log.clear();

        assertError(() -> at(AT_18_30).checkOut(new Command(GUARD, UUID.randomUUID(), open.id())),
                ErrorCode.INSUFFICIENT_POINTS);

        assertThat(wallets.balanceOf(studentWallet)).isEqualTo(4_999);
        assertThat(wallets.balanceOf(receiverWallet)).isEqualTo(100);
        assertThat(sessions.all().get(0).isOpen()).isTrue();                    // xe CHƯA được ra
        assertThat(transactions.all()).isEmpty();
        assertThat(log).noneMatch(entry -> entry.startsWith("lưu") || entry.startsWith("đóng"));
    }

    @Test
    void bamXacNhanHaiLanCungKey_chiTruMotLan_lanSauTraKetQuaCu_M3_06() {
        UUID key = UUID.randomUUID();
        Result first = at(AT_18_30).checkOut(new Command(GUARD, key, open.id()));

        Result again = at(AT_18_30.plusSeconds(5)).checkOut(new Command(GUARD, key, open.id()));

        assertThat(again.replayed()).isTrue();
        assertThat(again.payment().id()).isEqualTo(first.payment().id());
        assertThat(again.payment().createdAt()).isEqualTo(AT_18_30);            // kết quả CŨ, không phải giờ bấm lại
        assertThat(wallets.balanceOf(studentWallet)).isEqualTo(5_000);          // KHÔNG phải 0
        assertThat(transactions.all()).hasSize(1);
    }

    @Test
    void cungKeyKhacLuot_IDEMPOTENCY_KEY_REUSED() {
        UUID key = UUID.randomUUID();
        at(AT_18_30).checkOut(new Command(GUARD, key, open.id()));

        assertError(() -> at(AT_18_30).checkOut(new Command(GUARD, key, 999L)), ErrorCode.IDEMPOTENCY_KEY_REUSED);
        assertThat(transactions.all()).hasSize(1);
    }

    @Test
    void luotDaRa_keyKhac_SESSION_ALREADY_PAID_khongTruLanHai() {
        at(AT_18_30).checkOut(new Command(GUARD, UUID.randomUUID(), open.id()));

        assertError(() -> at(AT_18_30).checkOut(new Command(5L, UUID.randomUUID(), open.id())),
                ErrorCode.SESSION_ALREADY_PAID);
        assertThat(wallets.balanceOf(studentWallet)).isEqualTo(5_000);
        assertThat(transactions.all()).hasSize(1);
    }

    @Test
    void luotKhongTonTai_NO_OPEN_SESSION() {
        assertError(() -> at(AT_18_30).checkOut(new Command(GUARD, UUID.randomUUID(), 999L)),
                ErrorCode.NO_OPEN_SESSION);
        assertThat(transactions.all()).isEmpty();
    }

    private String where() {
        return inTransaction ? " (trong transaction)" : " (NGOÀI transaction)";
    }

    private static void assertError(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(expected));
    }
}