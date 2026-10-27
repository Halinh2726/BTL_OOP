package com.ptit.parking.parking.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.ptit.parking.catalog.domain.FeeRule;
import com.ptit.parking.parking.application.port.out.InMemoryParkingSessionRepository;
import com.ptit.parking.parking.application.port.out.InMemoryVehicleOwnerDirectory;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.VehicleOwner;
import com.ptit.parking.parking.application.usecase.CheckInUseCase.Command;
import com.ptit.parking.parking.domain.ParkingSession;
import com.ptit.parking.parking.domain.TimeSlotFeePolicy;
import com.ptit.parking.payment.application.port.out.InMemoryWalletRepository;
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.student.domain.PlateNumber;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * M2-05 (R05) — nhiều bảo vệ bấm "Xác nhận cho vào" CÙNG LÚC cho cùng một xe: chỉ đúng MỘT lượt gửi được tạo,
 * các request còn lại nhận SESSION_ALREADY_OPEN. Hai lớp bảo vệ, mỗi lớp một test:
 * <ol>
 *   <li>Khóa dòng xe: request sau chờ request trước xong, rồi thấy lượt vừa tạo.</li>
 *   <li>Ràng buộc "mỗi xe một lượt đang mở" của CSDL: kể cả khi việc khóa bị bỏ sót, request sau vẫn bị chặn khi ghi.</li>
 * </ol>
 */
class CheckInConcurrencyTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final long STUDENT = 3L;
    private static final VehicleOwner OWNER = new VehicleOwner(100L, new PlateNumber("29A12345"), "XE_MAY",
            STUDENT, "B21DCCN001", "Nguyễn Văn A");

    private final InMemoryWalletRepository wallets = new InMemoryWalletRepository();
    private final TimeSlotFeePolicy feePolicy = new TimeSlotFeePolicy(List.of(new FeeRule(LocalTime.MIDNIGHT, 3000)), VN);

    CheckInConcurrencyTest() {
        wallets.insert(new Wallet(null, STUDENT, 10_000));
    }

    @Test
    void khoaDongXe_10BaoVeCungXacNhan_chi1LuotMo_9NguoiSauThayLuotDangMo() throws Exception {
        // "Khóa dòng xe" giống CSDL: giữ tới khi transaction kết thúc
        ReentrantLock vehicleRow = new ReentrantLock();
        InMemoryVehicleOwnerDirectory vehicles = new InMemoryVehicleOwnerDirectory() {
            @Override
            public Optional<VehicleOwner> lockByPlate(PlateNumber plate) {
                vehicleRow.lock();
                return super.lockByPlate(plate);
            }
        };
        vehicles.add(OWNER);
        TransactionRunner transactions = new TransactionRunner() {
            @Override
            public <T> T inTransaction(Supplier<T> work) {
                try {
                    return work.get();
                } finally {
                    if (vehicleRow.isHeldByCurrentThread()) {
                        vehicleRow.unlock();                     // COMMIT/ROLLBACK nhả khóa
                    }
                }
            }
        };
        AtomicInteger saveAttempts = new AtomicInteger();
        InMemoryParkingSessionRepository sessions = new InMemoryParkingSessionRepository() {
            @Override
            public synchronized ParkingSession save(ParkingSession session) {
                saveAttempts.incrementAndGet();
                return super.save(session);
            }
        };
        CheckInUseCase checkIn = new CheckInUseCase(vehicles, sessions, wallets, feePolicy, transactions,
                Clock.system(VN));

        List<String> outcomes = runConcurrently(10, guard -> checkIn.checkIn(new Command(guard, "29A12345")));

        assertThat(outcomes).filteredOn("OK"::equals).hasSize(1);
        assertThat(outcomes).filteredOn("SESSION_ALREADY_OPEN"::equals).hasSize(9);
        assertThat(sessions.all()).hasSize(1);
        assertThat(saveAttempts).hasValue(1);                   // 9 người sau bị chặn NGAY ở bước kiểm tra
    }

    @Test
    void quenKhoa_haiRequestCungQuaBuocKiemTra_rangBuocVanChi1LuotMo() throws Exception {
        InMemoryVehicleOwnerDirectory vehicles = new InMemoryVehicleOwnerDirectory();   // lockByPlate KHÔNG chặn gì
        vehicles.add(OWNER);
        CyclicBarrier bothChecked = new CyclicBarrier(2);
        InMemoryParkingSessionRepository sessions = new InMemoryParkingSessionRepository() {
            @Override
            public ParkingSession save(ParkingSession session) {
                awaitQuietly(bothChecked);                       // cả hai đã thấy "chưa có lượt mở" rồi mới ghi
                return super.save(session);
            }
        };
        TransactionRunner directly = new TransactionRunner() {
            @Override
            public <T> T inTransaction(Supplier<T> work) {
                return work.get();
            }
        };
        CheckInUseCase checkIn = new CheckInUseCase(vehicles, sessions, wallets, feePolicy, directly,
                Clock.system(VN));

        List<String> outcomes = runConcurrently(2, guard -> checkIn.checkIn(new Command(guard, "29A12345")));

        assertThat(outcomes).containsExactlyInAnyOrder("OK", "SESSION_ALREADY_OPEN");
        assertThat(sessions.all()).hasSize(1);
    }

    /** Chạy n request cùng lúc (bảo vệ id 1..n); kết quả mỗi request là "OK" hoặc mã lỗi. */
    private static List<String> runConcurrently(int n, LongConsumer request) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();
            for (long guard = 1; guard <= n; guard++) {
                long guardId = guard;
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        request.accept(guardId);
                        return "OK";
                    } catch (DomainException e) {
                        return e.errorCode().name();
                    }
                }));
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

    private static void awaitQuietly(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Hai request không cùng tới bước ghi", e);
        }
    }
}