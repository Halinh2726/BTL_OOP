package com.ptit.parking.manager.configuration;
import com.ptit.parking.manager.application.usecase.ChangeGuardStatusUseCase;
import com.ptit.parking.manager.application.usecase.ListGuardsUseCase;

import com.ptit.parking.auth.domain.PasswordHasher;
import com.ptit.parking.catalog.application.port.out.ExchangeRateRepository;
import com.ptit.parking.manager.application.port.out.GuardAccountRepository;
import com.ptit.parking.manager.application.port.out.StudentDirectory;
import com.ptit.parking.manager.application.usecase.CreateGuardUseCase;
import com.ptit.parking.manager.application.usecase.DepositCashUseCase;
import com.ptit.parking.manager.application.usecase.LookupStudentUseCase;
import com.ptit.parking.payment.application.IdempotencyGuard;
import com.ptit.parking.payment.application.port.out.WalletRepository;
import com.ptit.parking.shared.application.port.out.AuditTrail;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.ptit.parking.manager.application.port.out.OwnerAccountDirectory;
import com.ptit.parking.manager.application.usecase.GetOwnerWalletUseCase;
import com.ptit.parking.manager.application.usecase.AdjustPointsUseCase;
import com.ptit.parking.payment.application.port.out.PointTransactionRepository;

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
                                          TransactionRunner transactions, AuditTrail audit, Clock clock) {
        return new CreateGuardUseCase(guards, hasher, transactions, audit, clock);
    }
    
    @Bean
    GetOwnerWalletUseCase getOwnerWalletUseCase(OwnerAccountDirectory owners, WalletRepository wallets) {
        return new GetOwnerWalletUseCase(owners, wallets);
    }

    /** Đợt 3 — thay cho "rút điểm tự do" (đã bỏ): giao dịch điều chỉnh có lý do. */
    @Bean
    AdjustPointsUseCase adjustPointsUseCase(StudentDirectory students, OwnerAccountDirectory owners,
                                            WalletRepository wallets, PointTransactionRepository ledger,
                                            IdempotencyGuard idempotency, Clock clock) {
        return new AdjustPointsUseCase(students, owners, wallets, ledger, idempotency, clock);
    }
    
    @Bean
    ListGuardsUseCase listGuardsUseCase(GuardAccountRepository guards) {
        return new ListGuardsUseCase(guards);
    }

    @Bean
    ChangeGuardStatusUseCase changeGuardStatusUseCase(GuardAccountRepository guards, TransactionRunner transactions,
                                                      AuditTrail audit, Clock clock) {
        return new ChangeGuardStatusUseCase(guards, transactions, audit, clock);
    }
}