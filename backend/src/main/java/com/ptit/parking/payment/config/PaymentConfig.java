package com.ptit.parking.payment.config;

import com.ptit.parking.payment.application.IdempotencyGuard;
import com.ptit.parking.payment.application.port.out.PointTransactionRepository;
import com.ptit.parking.payment.application.port.out.WalletRepository;
import com.ptit.parking.payment.application.usecase.GetMyWalletUseCase;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition Root của module payment: các thành phần dùng chung cho mọi nghiệp vụ cộng/trừ điểm. */
@Configuration
public class PaymentConfig {

    @Bean
    IdempotencyGuard idempotencyGuard(PointTransactionRepository transactions, TransactionRunner transactionRunner) {
        return new IdempotencyGuard(transactions, transactionRunner);
    }

    @Bean
    GetMyWalletUseCase getMyWalletUseCase(WalletRepository wallets) {
        return new GetMyWalletUseCase(wallets);
    }
}