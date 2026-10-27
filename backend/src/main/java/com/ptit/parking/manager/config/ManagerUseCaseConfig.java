package com.ptit.parking.manager.config;

import com.ptit.parking.auth.domain.PasswordHasher;
import com.ptit.parking.catalog.application.port.out.ExchangeRateRepository;
import com.ptit.parking.manager.application.port.out.GuardAccountRepository;
import com.ptit.parking.manager.application.port.out.StudentDirectory;
import com.ptit.parking.manager.application.usecase.CreateGuardUseCase;
import com.ptit.parking.manager.application.usecase.DepositCashUseCase;
import com.ptit.parking.manager.application.usecase.LookupStudentUseCase;
import com.ptit.parking.payment.application.IdempotencyGuard;
import com.ptit.parking.payment.application.port.out.WalletRepository;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition Root của module manager. */
@Configuration
public class ManagerUseCaseConfig {

    @Bean
    LookupStudentUseCase lookupStudentUseCase(StudentDirectory students, WalletRepository wallets) {
        return new LookupStudentUseCase(students, wallets);
    }

    @Bean
    DepositCashUseCase depositCashUseCase(StudentDirectory students, WalletRepository wallets,
                                          ExchangeRateRepository exchangeRates, IdempotencyGuard idempotency,
                                          Clock clock) {
        return new DepositCashUseCase(students, wallets, exchangeRates, idempotency, clock);
    }

    @Bean
    CreateGuardUseCase createGuardUseCase(GuardAccountRepository guards, PasswordHasher hasher,
                                          TransactionRunner transactions) {
        return new CreateGuardUseCase(guards, hasher, transactions);
    }
}