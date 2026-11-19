package com.ptit.parking.pass.config;

import com.ptit.parking.pass.application.port.out.MonthlyPassRepository;
import com.ptit.parking.pass.application.port.out.PassCatalog;
import com.ptit.parking.pass.application.usecase.BuyPassUseCase;
import com.ptit.parking.payment.application.port.out.PointTransactionRepository;
import com.ptit.parking.payment.application.port.out.ReceivingWalletProvider;
import com.ptit.parking.payment.application.port.out.WalletRepository;
import com.ptit.parking.shared.application.port.out.EventPublisher;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition Root của module pass (Đợt 5 — vé tháng). */
@Configuration
public class PassUseCaseConfig {

    @Bean
    BuyPassUseCase buyPassUseCase(MonthlyPassRepository passes, PassCatalog catalog, WalletRepository wallets,
                                  ReceivingWalletProvider ownerWallet, PointTransactionRepository ledger,
                                  TransactionRunner transactions, EventPublisher events, Clock clock) {
        return new BuyPassUseCase(passes, catalog, wallets, ownerWallet, ledger, transactions, events, clock);
    }
}
