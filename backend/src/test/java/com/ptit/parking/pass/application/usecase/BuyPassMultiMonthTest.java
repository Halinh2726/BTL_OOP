package com.ptit.parking.pass.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.pass.application.event.PassPurchased;
import com.ptit.parking.pass.application.port.out.MonthlyPassRepository;
import com.ptit.parking.pass.application.port.out.PassCatalog;
import com.ptit.parking.pass.domain.MonthlyPass;
import com.ptit.parking.payment.application.port.out.InMemoryPointTransactionRepository;
import com.ptit.parking.payment.application.port.out.InMemoryWalletRepository;
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** Kiểm tra mua vé 1 tháng và mua nhiều tháng liên tiếp theo đúng nguyên giá (không giảm giá/ưu đãi). */
class BuyPassMultiMonthTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant NOW = Instant.parse("2026-11-15T03:00:00Z"); // 2026-11 giờ VN
    private static final long STUDENT = 3L;
    private static final long OWNER = 2L;
    private static final long VEHICLE = 10L;

    private final InMemoryPasses passes = new InMemoryPasses();
    private final InMemoryWalletRepository wallets = new InMemoryWalletRepository();
    private final InMemoryPointTransactionRepository ledger = new InMemoryPointTransactionRepository();
    private final List<Object> published = new ArrayList<>();
    private final long receiverWallet = wallets.insert(new Wallet(null, OWNER, 0));

    private final PassCatalog catalog = new PassCatalog() {
        @Override
        public Optional<Long> price(String vehicleTypeCode) {
            return "XE_MAY".equals(vehicleTypeCode) ? Optional.of(100_000L) : Optional.empty();
        }

        @Override
        public List<VehicleInfo> vehiclesOf(long studentId) {
            return studentId == STUDENT
                    ? List.of(new VehicleInfo(VEHICLE, "29A12345", "XE_MAY", "Xe máy", true))
                    : List.of();
        }
    };

    private final TransactionRunner directly = new TransactionRunner() {
        @Override
        public <T> T inTransaction(Supplier<T> work) {
            return work.get();
        }
    };

    private BuyPassUseCase useCase() {
        return new BuyPassUseCase(passes, catalog, wallets, () -> receiverWallet, ledger, directly,
                published::add, Clock.fixed(NOW, VN));
    }

    @Test
    void muaNhieuThang_tinhDungNguyenGiaKhongGiamGia_quaNamVanLienTiep() {
        long studentWallet = wallets.insert(new Wallet(null, STUDENT, 500_000));
        BuyPassUseCase uc = useCase();
        UUID key = UUID.randomUUID();

        BuyPassUseCase.Purchase bought = uc.buy(new BuyPassUseCase.Command(
                STUDENT, key, VEHICLE, YearMonth.of(2026, 11), 3));

        assertThat(bought.monthsCount()).isEqualTo(3);
        assertThat(bought.totalPrice()).isEqualTo(300_000);
        assertThat(bought.validUntil()).isEqualTo(LocalDate.of(2027, 1, 31));
        assertThat(bought.balanceAfter()).isEqualTo(200_000);
        assertThat(wallets.balanceOf(studentWallet)).isEqualTo(200_000);
        assertThat(wallets.balanceOf(receiverWallet)).isEqualTo(300_000);
        assertThat(passes.stored).extracting(MonthlyPass::month).containsExactly(
                YearMonth.of(2026, 11), YearMonth.of(2026, 12), YearMonth.of(2027, 1));
        assertThat(passes.stored).allSatisfy(p -> assertThat(p.pricePoints()).isEqualTo(100_000));

        assertThat(published).singleElement().isInstanceOf(PassPurchased.class);
        PassPurchased event = (PassPurchased) published.getFirst();
        assertThat(event.notices().getFirst().title()).contains("3 tháng").doesNotContain("ưu đãi");

        // Idempotency replay
        BuyPassUseCase.Purchase replay = uc.buy(new BuyPassUseCase.Command(
                STUDENT, key, VEHICLE, YearMonth.of(2026, 11), 3));
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.totalPrice()).isEqualTo(300_000);
    }

    @Test
    void mua1ThangMacDinh_vaChanSoThangKhongHopLeHoacTrungThang() {
        wallets.insert(new Wallet(null, STUDENT, 400_000));
        BuyPassUseCase uc = useCase();

        assertThatThrownBy(() -> uc.buy(new BuyPassUseCase.Command(
                STUDENT, UUID.randomUUID(), VEHICLE, YearMonth.of(2026, 11), 0)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> uc.buy(new BuyPassUseCase.Command(
                STUDENT, UUID.randomUUID(), VEHICLE, YearMonth.of(2026, 11), 13)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        BuyPassUseCase.Purchase single = uc.buy(new BuyPassUseCase.Command(
                STUDENT, UUID.randomUUID(), VEHICLE, YearMonth.of(2026, 12)));
        assertThat(single.monthsCount()).isEqualTo(1);
        assertThat(single.totalPrice()).isEqualTo(100_000);

        assertThatThrownBy(() -> uc.buy(new BuyPassUseCase.Command(
                STUDENT, UUID.randomUUID(), VEHICLE, YearMonth.of(2026, 11), 2)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.PASS_ALREADY_EXISTS));
    }

    private static final class InMemoryPasses implements MonthlyPassRepository {
        final List<MonthlyPass> stored = new ArrayList<>();
        private final AtomicLong seq = new AtomicLong(1);

        @Override
        public MonthlyPass insert(MonthlyPass pass) {
            MonthlyPass saved = pass.withId(seq.getAndIncrement());
            stored.add(saved);
            return saved;
        }

        @Override
        public Optional<MonthlyPass> findFor(long vehicleId, YearMonth month) {
            return stored.stream().filter(p -> p.vehicleId() == vehicleId && p.month().equals(month)).findFirst();
        }

        @Override
        public List<MonthlyPass> findByStudent(long studentId) {
            return stored.stream().filter(p -> p.studentId() == studentId).toList();
        }

        @Override
        public List<MonthlyPass> findByTransactionId(long transactionId) {
            return stored.stream().filter(p -> p.transactionId() == transactionId).toList();
        }

        @Override
        public List<MonthlyPass> findForVehicleInMonths(long vehicleId, List<YearMonth> months) {
            return stored.stream().filter(p -> p.vehicleId() == vehicleId && months.contains(p.month())).toList();
        }
    }
}
