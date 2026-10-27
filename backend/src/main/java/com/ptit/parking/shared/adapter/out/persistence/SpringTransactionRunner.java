package com.ptit.parking.shared.adapter.out.persistence;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ptit.parking.shared.application.port.out.TransactionRunner;

/**
 * Cài đặt {@link TransactionRunner} bằng transaction của Spring. Khối công việc ném exception
 * thì toàn bộ thay đổi trong khối bị ROLLBACK.
 */
@Component
class SpringTransactionRunner implements TransactionRunner {

    private final TransactionTemplate template;

    SpringTransactionRunner(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return template.execute(status -> work.get());
    }
}