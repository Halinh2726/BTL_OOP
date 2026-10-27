package com.ptit.parking.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.payment.application.IdempotencyGuard.Execution;
import com.ptit.parking.payment.application.port.out.InMemoryPointTransactionRepository;
import com.ptit.parking.payment.domain.CashDeposit;
import com.ptit.parking.payment.domain.ExchangeRate;
import com.ptit.parking.payment.domain.TransactionType;
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** Đặc tả API mục 6 — mỗi Idempotency-Key tạo NHIỀU NHẤT một giao dịch. */
class IdempotencyGuardTest {

    private static final Instant NOW = Instant.parse("2026-10-27T01:00:00Z");
    private static final UUID KEY = UUID.randomUUID();

    private final InMemoryPointTransactionRepository transactions = new InMemoryPointTransactionRepository();
    private final TransactionRunner directly = new TransactionRunner() {
        @Override
        public <T> T inTransaction(Supplier<T> work) {
            return work.get();
        }
    };
    private final IdempotencyGuard guard = new IdempotencyGuard(transactions, directly);
    private final AtomicInteger executions = new AtomicInteger();

    private final String hash = RequestFingerprint.of(TransactionType.DOI_DIEM, 1L, "B21DCCN001", 10_000L);

    @Test
    void keyMoi_chayNghiepVuMotLan_vaTraGiaoDichDaLuu() {
        Execution<CashDeposit> execution = guard.execute(KEY, hash, CashDeposit.class, this::deposit);

        assertThat(execution.replayed()).isFalse();
        assertThat(execution.transaction().id()).isEqualTo(1L);           // bản ĐÃ LƯU, có id
        assertThat(executions).hasValue(1);
        assertThat(transactions.all()).hasSize(1);
    }

    @Test
    void guiLaiCungKeyCungNoiDung_khongChayLai_traKetQuaCu() {
        guard.execute(KEY, hash, CashDeposit.class, this::deposit);

        Execution<CashDeposit> again = guard.execute(KEY, hash, CashDeposit.class, this::deposit);

        assertThat(again.replayed()).isTrue();
        assertThat(again.transaction().id()).isEqualTo(1L);
        assertThat(executions).hasValue(1);                                // nghiệp vụ KHÔNG chạy lần hai
        assertThat(transactions.all()).hasSize(1);
    }

    @Test
    void cungKeyKhacNoiDung_baoIdempotencyKeyReused() {
        guard.execute(KEY, hash, CashDeposit.class, this::deposit);
        String otherHash = RequestFingerprint.of(TransactionType.DOI_DIEM, 1L, "B21DCCN001", 99_000L);

        assertThatThrownBy(() -> guard.execute(KEY, otherHash, CashDeposit.class, this::deposit))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
        assertThat(transactions.all()).hasSize(1);
    }

    @Test
    void haiRequestSongSongCungKey_requestSauTraLaiKetQuaCuaRequestTruoc() {
        // Request khác cùng key ghi xong đúng lúc request này chuẩn bị lưu -> CSDL chặn trùng
        transactions.beforeNextSave(() -> transactions.save(deposit(), hash));

        Execution<CashDeposit> execution = guard.execute(KEY, hash, CashDeposit.class, this::deposit);

        assertThat(execution.replayed()).isTrue();
        assertThat(execution.transaction().id()).isEqualTo(1L);
        assertThat(transactions.all()).hasSize(1);
    }

    @Test
    void loiNghiepVu_khongLuuGiaoDich() {
        assertThatThrownBy(() -> guard.execute(KEY, hash, CashDeposit.class, () -> {
            throw new DomainException(ErrorCode.STUDENT_NOT_FOUND);
        })).isInstanceOf(DomainException.class);
        assertThat(transactions.all()).isEmpty();
    }

    private CashDeposit deposit() {
        executions.incrementAndGet();
        return CashDeposit.execute(new Wallet(5L, 3L, 0), 10_000, new ExchangeRate(1), 1L, KEY, NOW);
    }
}