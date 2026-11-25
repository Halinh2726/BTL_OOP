package com.ptit.parking.guest.configuration;

import com.ptit.parking.catalog.application.port.out.ExchangeRateRepository;
import com.ptit.parking.catalog.application.port.out.FeeRuleRepository;
import com.ptit.parking.gate.application.usecase.ReportIncidentUseCase;
import com.ptit.parking.guest.application.GuestParking;
import com.ptit.parking.guest.application.GuestParkingStore;
import com.ptit.parking.parking.application.port.out.OpenShiftLookup;
import com.ptit.parking.parking.application.port.out.ParkingPolicyRepository;
import com.ptit.parking.parking.application.port.out.VietQrGenerator;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.student.application.port.out.VehicleRepository;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
class GuestParkingConfig {
    @Bean
    GuestParking guestParking(GuestParkingStore store, VehicleRepository vehicles, FeeRuleRepository tariffs,
                              ExchangeRateRepository rates, ParkingPolicyRepository policies, OpenShiftLookup shifts,
                              VietQrGenerator vietQr, ReportIncidentUseCase incidents, TransactionRunner transactions,
                              Clock clock) {
        return new GuestParking(store, vehicles, tariffs, rates, policies, shifts, vietQr, incidents, transactions, clock);
    }

    @Bean
    GuestPrivacyJob guestPrivacyJob(GuestParkingStore store, Clock clock) {
        return new GuestPrivacyJob(store, clock);
    }

    record GuestPrivacyJob(GuestParkingStore store, Clock clock) {
        @Scheduled(fixedDelayString = "PT24H", initialDelayString = "PT1M")
        public void anonymize() {
            store.anonymizeBefore(clock.instant().minus(90, ChronoUnit.DAYS));
        }
    }
}
