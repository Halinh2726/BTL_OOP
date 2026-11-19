package com.ptit.parking.owner.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.owner.application.port.out.OwnerFinanceQuery;
import com.ptit.parking.owner.application.port.out.WithdrawalRequestRepository;
import com.ptit.parking.owner.domain.WithdrawalRequest;
import com.ptit.parking.owner.domain.WithdrawalStatus;
import com.ptit.parking.payment.application.port.out.InMemoryPointTransactionRepository;
import com.ptit.parking.payment.application.port.out.InMemoryWalletRepository;
import com.ptit.parking.payment.domain.CashWithdrawal;
import com.ptit.parking.payment.domain.ExchangeRate;
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Đợt 3 — R15 (không giữ chỗ quá số điểm), R16 (không vượt quỹ tiền mặt), R17 (trả một lần), R18. */
class WithdrawalUseCasesTest {

    private static final long OWNER = 2L;
    private static final long MANAGER = 1L;
    private static final TransactionRunner DIRECTLY = new TransactionRunner() {
        @Override
        public <T> T inTransaction(Supplier<T> work) {
            return work.get();
        }
    };

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-20T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private final InMemoryWalletRepository wallets = new InMemoryWalletRepository();
    private final InMemoryPointTransactionRepository ledger = new InMemoryPointTransactionRepository();
    private final FakeRequests requests = new FakeRequests();
    private final FakeFinance finance = new FakeFinance();
    private OwnerWithdrawalUseCase owner;
    private ProcessWithdrawalUseCase manager;

    @BeforeEach
    void chuBaiCo10000Diem_quyTienMat50000() {
        long walletId = wallets.insert(new Wallet(null, OWNER, 10_000));
        finance.account = new OwnerFinanceQuery.ReceivingAccount(OWNER, "Lê Văn C", walletId);
        finance.cashHeld = 50_000;
        owner = new OwnerWithdrawalUseCase(requests, finance, wallets, () -> new ExchangeRate(1), DIRECTLY, clock);
        manager = new ProcessWithdrawalUseCase(requests, finance, wallets, ledger, DIRECTLY, clock);
    }

    @Test
    void R15_tongYeuCauDangChoKhongVuotSoDiem_taoChuaTruDiem() {
        owner.create(OWNER, 6_000);
        assertThat(wallets.balanceOf(finance.account.walletId())).isEqualTo(10_000);     // chưa trừ

        assertThatThrownBy(() -> owner.create(OWNER, 5_000))                             // 6.000 + 5.000 > 10.000
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.WITHDRAWAL_EXCEEDS_AVAILABLE));
        assertThat(owner.create(OWNER, 4_000).status()).isEqualTo(WithdrawalStatus.CHO_XU_LY);
    }

    @Test
    void traTien_luocTruDiem_ghiGiaoDichRutDiem_bamLaiKhongTruLanHai() {
        long id = owner.create(OWNER, 3_000).id();

        ProcessWithdrawalUseCase.PaidResult first = manager.pay(MANAGER, id);
        ProcessWithdrawalUseCase.PaidResult again = manager.pay(MANAGER, id);

        assertThat(first.replayed()).isFalse();
        assertThat(again.replayed()).isTrue();                                            // R17
        assertThat(wallets.balanceOf(finance.account.walletId())).isEqualTo(7_000);
        assertThat(ledger.all()).hasSize(1).first().isInstanceOf(CashWithdrawal.class);
        assertThat(first.request().transactionId()).isEqualTo(ledger.all().get(0).id());
    }

    @Test
    void R16_quyTienMatKhongDu_khongTraDuoc_khongTruDiem() {
        finance.cashHeld = 2_000;
        long id = owner.create(OWNER, 3_000).id();

        assertThatThrownBy(() -> manager.pay(MANAGER, id))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CASH_FUND_INSUFFICIENT));
        assertThat(wallets.balanceOf(finance.account.walletId())).isEqualTo(10_000);
        assertThat(ledger.all()).isEmpty();
    }

    @Test
    void R18_chuBaiKhongTuTraTienChoMinh() {
        long id = owner.create(OWNER, 3_000).id();

        assertThatThrownBy(() -> manager.pay(OWNER, id))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.SELF_PROCESSING_FORBIDDEN));
    }

    @Test
    void tuChoi_giaiPhongDiemDaGiuCho_taoDuocYeuCauMoi() {
        long id = owner.create(OWNER, 10_000).id();
        manager.reject(MANAGER, id, "Quỹ đang kiểm kê, tạo lại sau 17:00");

        assertThat(owner.create(OWNER, 10_000).status()).isEqualTo(WithdrawalStatus.CHO_XU_LY);
    }

    @Test
    void nguoiKhongPhaiChuBai_khongTaoDuocYeuCau() {
        assertThatThrownBy(() -> owner.create(99L, 1_000))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    // ===== Kho giả =====

    static final class FakeRequests implements WithdrawalRequestRepository {
        final List<WithdrawalRequest> all = new ArrayList<>();

        @Override
        public WithdrawalRequest save(WithdrawalRequest request) {
            if (request.id() != null) {
                return request;
            }
            WithdrawalRequest saved = request.withId(all.size() + 1);
            all.add(saved);
            return saved;
        }

        @Override
        public Optional<WithdrawalRequest> findById(long id) {
            return all.stream().filter(r -> r.id() == id).findFirst();
        }

        @Override
        public Optional<WithdrawalRequest> lockById(long id) {
            return findById(id);
        }

        @Override
        public List<WithdrawalRequest> findByStatus(WithdrawalStatus status, int limit) {
            return all.stream().filter(r -> status == null || r.status() == status).limit(limit).toList();
        }

        @Override
        public List<WithdrawalRequest> findByOwner(long ownerId, int limit) {
            return all.stream().filter(r -> r.ownerId() == ownerId).limit(limit).toList();
        }

        @Override
        public long sumPendingPoints(long ownerId) {
            return all.stream().filter(r -> r.ownerId() == ownerId && r.status().isPending())
                    .mapToLong(WithdrawalRequest::points).sum();
        }
    }

    static final class FakeFinance implements OwnerFinanceQuery {
        ReceivingAccount account;
        long cashHeld;

        @Override
        public ReceivingAccount receivingAccount() {
            return account;
        }

        @Override
        public long cashHeldByManager() {
            return cashHeld;
        }

        @Override
        public List<DailyRevenue> dailyRevenue(Instant from, Instant to, ZoneId zone) {
            return List.of();
        }

        @Override
        public Map<Long, String> namesOf(Collection<Long> userIds) {
            return Map.of();
        }
    }
}
