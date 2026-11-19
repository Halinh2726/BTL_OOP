package com.ptit.parking.report.config;

import com.ptit.parking.report.application.port.out.CashPositionQuery;
import com.ptit.parking.report.application.port.out.LedgerQuery;
import com.ptit.parking.report.application.port.out.ReconciliationRepository;
import com.ptit.parking.report.application.port.out.StudentProfileQuery;
import com.ptit.parking.report.application.port.out.TrafficQuery;
import com.ptit.parking.report.application.usecase.LedgerUseCase;
import com.ptit.parking.report.application.usecase.ReconciliationUseCase;
import com.ptit.parking.report.application.usecase.StudentProfileUseCase;
import com.ptit.parking.report.application.usecase.TrafficStatsUseCase;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition Root của module report (Đợt 4a): use case là lớp Java thuần, Spring chỉ nối dây ở đây. */
@Configuration
public class ReportUseCaseConfig {

    @Bean
    StudentProfileUseCase studentProfileUseCase(StudentProfileQuery students, LedgerQuery ledger) {
        return new StudentProfileUseCase(students, ledger);
    }

    @Bean
    LedgerUseCase ledgerUseCase(LedgerQuery ledger, Clock clock) {
        return new LedgerUseCase(ledger, clock);
    }

    @Bean
    ReconciliationUseCase reconciliationUseCase(CashPositionQuery positions, ReconciliationRepository reconciliations,
                                                TransactionRunner transactions, Clock clock) {
        return new ReconciliationUseCase(positions, reconciliations, transactions, clock);
    }

    @Bean
    TrafficStatsUseCase trafficStatsUseCase(TrafficQuery traffic, Clock clock) {
        return new TrafficStatsUseCase(traffic, clock);
    }
}
