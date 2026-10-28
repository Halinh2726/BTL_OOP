package com.ptit.parking.parking.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.student.domain.PlateNumber;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongFunction;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * M3-06 — nhiều bảo vệ bấm "Xác nhận cho ra" CÙNG LÚC cho cùng một lượt gửi: sinh viên chỉ bị trừ phí MỘT lần.
 * "Khóa dòng lượt gửi" giả lập bằng ReentrantLock, giữ tới hết transaction giống SELECT ... FOR UPDATE.
 */
class CheckOutConcurrencyTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant AT_18_30 = Instant.parse("2026-10-05T11:30:00Z");
    private static final long STUDENT = 3L;
    private static final VehicleOwner OWNER = new VehicleOwner(100L, new PlateNumber("29A12345"), "XE_MAY",
            STUDENT, "B21DCCN001", "Nguyễn Văn A");

    private final ReentrantLock sessionRow = new ReentrantLock();
    private final InMemoryVehicleOwnerDirectory vehicles = new InMemoryVehicleOwnerDirectory();
    private final InMemoryParkingSessionRepository sessions = new InMemoryParkingSessionRepository() {
        @Override
        public Optional<ParkingSession> lockById(long sessionId) {
            sessionRow.lock();
            return super.lockById(sessionId);
        }
    };
    private final TransactionRunner transactions = new TransactionRunner() {
        @Override
        public <T> T inTransaction(Supplier<T> work) {
            try {
                return work.get();
            } finally {
                if (sessionRow.isHeldByCurrentThread()) {
                    sessionRow.unlock();                                  // COMMIT/ROLLBACK nhả khóa
                }
            }
        }
    };
    private final InMemoryWalletRepository wallets = new InMemoryWalletRepository();
    private final InMemoryPointTransactionRepository ledger = new InMemoryPointTransactionRepository();
    private final long receiverWallet = wallets.insert(new Wallet(null, 2L, 0));
    private final long studentWallet = wallets.insert(new Wallet(null, STUDENT, 10_000));

    private final CheckOutUseCase checkOut = new CheckOutUseCase(sessions, vehicles,
            new PayParkingFeeUseCase(wallets, () -> receiverWallet), new IdempotencyGuard(ledger, transactions),
            new TimeSlotFeePolicy(List.of(new FeeRule(LocalTime.MIDNIGHT, 3000), new FeeRule(LocalTime.of(18, 0), 5000)), VN),
            Clock.fixed(AT_18_30, VN));

    private final long sessionId;

    CheckOutConcurrencyTest() {
        vehicles.add(OWNER);
        sessionId = sessions.markOpen(OWNER.vehicleId(), AT_18_30.minusSeconds(3600)).id();
    }

    @Test
    void muoiBaoVeKhacKey_cungXacNhan_chiTru1Lan_9NguoiSauBaoDaThanhToan() throws Exception {
        List<String> outcomes = runConcurrently(10,
                guard -> outcome(() -> checkOut.checkOut(new Command(guard, UUID.randomUUID(), sessionId))));

        assertThat(outcomes).filteredOn("MỚI"::equals).hasSize(1);
        assertThat(outcomes).filteredOn("SESSION_ALREADY_PAID"::equals).hasSize(9);
        assertThat(ledger.all()).hasSize(1);
        assertThat(wallets.balanceOf(studentWallet)).isEqualTo(5_000);          // KHÔNG phải -40.000
        assertThat(wallets.balanceOf(receiverWallet)).isEqualTo(5_000);
    }

    @Test
    void muoiRequestCungKey_guiLapDongThoi_1GiaoDich_9RequestNhanLaiKetQuaCu() throws Exception {
        UUID key = UUID.randomUUID();

        List<String> outcomes = runConcurrently(10,
                ignored -> outcome(() -> checkOut.checkOut(new Command(4L, key, sessionId))));

        assertThat(outcomes).filteredOn("MỚI"::equals).hasSize(1);
        assertThat(outcomes).filteredOn("TRẢ LẠI"::equals).hasSize(9);         // 201 kèm Idempotent-Replayed
        assertThat(ledger.all()).hasSize(1);
        assertThat(wallets.balanceOf(studentWallet)).isEqualTo(5_000);
    }

    /** "MỚI" — giao dịch vừa tạo; "TRẢ LẠI" — kết quả cũ (replayed); còn lại là mã lỗi. */
    private static String outcome(Supplier<Result> call) {
        try {
            return call.get().replayed() ? "TRẢ LẠI" : "MỚI";
        } catch (DomainException e) {
            return e.errorCode().name();
        }
    }

    /** Chạy n request cùng lúc (bảo vệ id 1..n). */
    private static List<String> runConcurrently(int n, LongFunction<String> request) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();
            for (long guard = 1; guard <= n; guard++) {
                long guardId = guard;
                Callable<String> task = () -> {
                    start.await();
                    return request.apply(guardId);
                };
                futures.add(pool.submit(task));
            }
            start.countDown();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> future : futures) {
                outcomes.add(future.get(10, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }
}
