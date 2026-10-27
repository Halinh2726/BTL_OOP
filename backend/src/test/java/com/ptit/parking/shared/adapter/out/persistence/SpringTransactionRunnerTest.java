package com.ptit.parking.shared.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** Chính sách thử lại khi tranh chấp khóa — kiểm tra bằng trình quản lý transaction giả, không cần CSDL. */
class SpringTransactionRunnerTest {

    private final AtomicInteger rollbacks = new AtomicInteger();
    private final SpringTransactionRunner transactions = new SpringTransactionRunner(new PlatformTransactionManager() {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
            rollbacks.incrementAndGet();
        }
    });

    private final AtomicInteger attempts = new AtomicInteger();

    @Test
    void tranhChapKhoa_haiLanRoiThanhCong_traKetQuaLanThuBa() {
        String result = transactions.inTransaction(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new CannotAcquireLockException("Deadlock found when trying to get lock");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts).hasValue(3);
        assertThat(rollbacks).hasValue(2);                       // mỗi lần thất bại đều rollback sạch
    }

    @Test
    void tranhChapKhoa_quaBaLan_baoConcurrentUpdate() {
        assertThatThrownBy(() -> transactions.inTransaction(() -> {
            attempts.incrementAndGet();
            throw new CannotAcquireLockException("Lock wait timeout exceeded");
        }))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONCURRENT_UPDATE));
        assertThat(attempts).hasValue(SpringTransactionRunner.MAX_ATTEMPTS);
    }

    @Test
    void loiNghiepVu_khongThuLai_vaRollback() {
        assertThatThrownBy(() -> transactions.inTransaction(() -> {
            attempts.incrementAndGet();
            throw new DomainException(ErrorCode.INSUFFICIENT_POINTS);
        }))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_POINTS));
        assertThat(attempts).hasValue(1);
        assertThat(rollbacks).hasValue(1);
    }
}