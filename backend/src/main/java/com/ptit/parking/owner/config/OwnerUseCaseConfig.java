package com.ptit.parking.owner.config;

import com.ptit.parking.catalog.application.port.out.ExchangeRateRepository;
import com.ptit.parking.owner.application.port.out.OwnerFinanceQuery;
import com.ptit.parking.owner.application.port.out.WithdrawalRequestRepository;
import com.ptit.parking.owner.application.usecase.OwnerFinanceUseCase;
import com.ptit.parking.owner.application.usecase.OwnerWithdrawalUseCase;
import com.ptit.parking.owner.application.usecase.ProcessWithdrawalUseCase;
import com.ptit.parking.payment.application.port.out.PointTransactionRepository;
import com.ptit.parking.payment.application.port.out.WalletRepository;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition Root của module owner (chủ bãi xe). */
@Configuration
public class OwnerUseCaseConfig {

    @Bean
    OwnerWithdrawalUseCase ownerWithdrawalUseCase(WithdrawalRequestRepository requests, OwnerFinanceQuery finance,
                                                  WalletRepository wallets, ExchangeRateRepository exchangeRates,
                                                  TransactionRunner transactions, Clock clock) {
        return new OwnerWithdrawalUseCase(requests, finance, wallets, exchangeRates, transactions, clock);
    }

    @Bean
    ProcessWithdrawalUseCase processWithdrawalUseCase(WithdrawalRequestRepository requests, OwnerFinanceQuery finance,
                                                      WalletRepository wallets, PointTransactionRepository ledger,
                                                      TransactionRunner transactions, Clock clock) {
        return new ProcessWithdrawalUseCase(requests, finance, wallets, ledger, transactions, clock);
    }

    @Bean
    OwnerFinanceUseCase ownerFinanceUseCase(OwnerFinanceQuery finance, WithdrawalRequestRepository requests,
                                            WalletRepository wallets, ExchangeRateRepository exchangeRates, Clock clock) {
        return new OwnerFinanceUseCase(finance, requests, wallets, exchangeRates, clock);
    }
}
