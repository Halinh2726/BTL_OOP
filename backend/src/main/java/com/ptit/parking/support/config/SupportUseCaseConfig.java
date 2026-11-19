package com.ptit.parking.support.config;

import com.ptit.parking.shared.application.port.out.EventPublisher;
import com.ptit.parking.payment.application.port.out.PointTransactionRepository;
import com.ptit.parking.payment.application.port.out.ReceivingWalletProvider;
import com.ptit.parking.payment.application.port.out.WalletRepository;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.support.application.PointPayout;
import com.ptit.parking.support.application.port.out.ComplaintDirectory;
import com.ptit.parking.support.application.port.out.ComplaintRepository;
import com.ptit.parking.support.application.usecase.CompensationDecisionUseCase;
import com.ptit.parking.support.application.usecase.HandleComplaintUseCase;
import com.ptit.parking.support.application.usecase.StudentComplaintUseCase;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition Root của module support (Đợt 4b — khiếu nại, bồi thường). */
@Configuration
public class SupportUseCaseConfig {

    @Bean
    PointPayout pointPayout(WalletRepository wallets, ReceivingWalletProvider ownerWallet,
                            PointTransactionRepository ledger) {
        return new PointPayout(wallets, ownerWallet, ledger);
    }

    @Bean
    StudentComplaintUseCase studentComplaintUseCase(ComplaintRepository complaints, ComplaintDirectory directory,
                                                    TransactionRunner transactions, EventPublisher events,
                                                    Clock clock) {
        return new StudentComplaintUseCase(complaints, directory, transactions, events, clock);
    }

    @Bean
    HandleComplaintUseCase handleComplaintUseCase(ComplaintRepository complaints, ComplaintDirectory directory,
                                                  PointPayout payout, TransactionRunner transactions,
                                                  EventPublisher events, Clock clock) {
        return new HandleComplaintUseCase(complaints, directory, payout, transactions, events, clock);
    }

    @Bean
    CompensationDecisionUseCase compensationDecisionUseCase(ComplaintRepository complaints,
                                                            ComplaintDirectory directory, PointPayout payout,
                                                            TransactionRunner transactions, EventPublisher events,
                                                            Clock clock) {
        return new CompensationDecisionUseCase(complaints, directory, payout, transactions, events, clock);
    }
}
