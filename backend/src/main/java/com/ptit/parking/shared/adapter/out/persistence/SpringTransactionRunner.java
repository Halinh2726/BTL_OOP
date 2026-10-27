package com.ptit.parking.shared.adapter.out.persistence;

import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cài đặt {@link TransactionRunner} bằng transaction của Spring. Khối công việc ném exception
 * thì toàn bộ thay đổi trong khối bị ROLLBACK.
 * <p>Tranh chấp khóa (deadlock, hoặc chờ khóa quá 5 giây): CSDL đã hủy transaction nên chạy lại TỪ ĐẦU là an toàn
 * — thử tối đa 3 lần, vẫn không được thì báo {@code CONCURRENT_UPDATE} để người dùng thử lại sau.
 */
@Component
class SpringTransactionRunner implements TransactionRunner {

    static final int MAX_ATTEMPTS = 3;

    private static final Logger log = LoggerFactory.getLogger(SpringTransactionRunner.class);

    private final TransactionTemplate template;

    SpringTransactionRunner(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        for (int attempt = 1; ; attempt++) {
            try {
                return template.execute(status -> work.get());
            } catch (PessimisticLockingFailureException e) {
                log.warn("Tranh chấp khóa CSDL, lần thử {}/{}: {}", attempt, MAX_ATTEMPTS, e.getClass().getSimpleName());
                if (attempt == MAX_ATTEMPTS) {
                    throw new DomainException(ErrorCode.CONCURRENT_UPDATE);
                }
            }
        }
    }
}