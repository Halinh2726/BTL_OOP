package com.ptit.parking.parking.config;

import com.ptit.parking.catalog.application.port.out.ExchangeRateRepository;
import com.ptit.parking.catalog.application.port.out.FeeRuleRepository;
import com.ptit.parking.parking.adapter.out.vietqr.DefaultVietQrGenerator;
import com.ptit.parking.parking.application.PassAwareFeePolicy;
import com.ptit.parking.parking.application.port.out.GateCashRepository;
import com.ptit.parking.parking.application.port.out.OpenShiftLookup;
import com.ptit.parking.parking.application.port.out.ParkingPolicyRepository;
import com.ptit.parking.parking.application.port.out.ParkingSessionRepository;
import com.ptit.parking.parking.application.port.out.PassCoverage;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory;
import com.ptit.parking.parking.application.port.out.VietQrGenerator;
import com.ptit.parking.parking.application.usecase.CheckInUseCase;
import com.ptit.parking.parking.application.usecase.CheckOutByCashUseCase;
import com.ptit.parking.parking.application.usecase.CheckOutUseCase;
import com.ptit.parking.parking.application.usecase.PayParkingFeeByVietQrUseCase;
import com.ptit.parking.parking.application.usecase.PreviewCheckInUseCase;
import com.ptit.parking.parking.application.usecase.PreviewCheckOutUseCase;
import com.ptit.parking.parking.application.usecase.SuggestPlatesUseCase;
import com.ptit.parking.parking.domain.FeePolicy;
import com.ptit.parking.parking.domain.OvernightAwareFeePolicy;
import com.ptit.parking.parking.domain.TimeSlotFeePolicy;
import com.ptit.parking.payment.application.IdempotencyGuard;
import com.ptit.parking.payment.application.port.out.PointTransactionRepository;
import com.ptit.parking.payment.application.port.out.ReceivingWalletProvider;
import com.ptit.parking.payment.application.port.out.WalletRepository;
import com.ptit.parking.payment.application.usecase.GetBankConfigUseCase;
import com.ptit.parking.payment.application.usecase.PayParkingFeeUseCase;
import com.ptit.parking.shared.application.port.out.EventPublisher;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition Root của module parking. Nối chuỗi Decorator cho {@link FeePolicy}:
 * {@code OvernightAwareFeePolicy(PassAwareFeePolicy(TimeSlotFeePolicy))}.
 */
@Configuration
public class ParkingUseCaseConfig {

    @Bean
    FeePolicy feePolicy(FeeRuleRepository feeRules, PassCoverage passes,
                        ParkingPolicyRepository policyRepository, Clock clock) {
        FeePolicy regular = new PassAwareFeePolicy(
                new TimeSlotFeePolicy(feeRules.allFeeRules(), clock.getZone()), passes, clock.getZone());
        return new OvernightAwareFeePolicy(regular, policyRepository::current, clock.getZone());
    }

    @Bean
    VietQrGenerator vietQrGenerator(GetBankConfigUseCase getBankConfig) {
        return new DefaultVietQrGenerator(getBankConfig::currentConfig);
    }

    @Bean
    PreviewCheckInUseCase previewCheckInUseCase(VehicleOwnerDirectory vehicles, ParkingSessionRepository sessions,
                                                WalletRepository wallets, FeePolicy feePolicy, Clock clock) {
        return new PreviewCheckInUseCase(vehicles, sessions, wallets, feePolicy, clock);
    }

    @Bean
    PreviewCheckOutUseCase previewCheckOutUseCase(VehicleOwnerDirectory vehicles, ParkingSessionRepository sessions,
                                                  WalletRepository wallets, FeePolicy feePolicy,
                                                  VietQrGenerator vietQrGenerator, Clock clock) {
        return new PreviewCheckOutUseCase(vehicles, sessions, wallets, feePolicy, vietQrGenerator, clock);
    }

    @Bean
    SuggestPlatesUseCase suggestPlatesUseCase(VehicleOwnerDirectory vehicles) {
        return new SuggestPlatesUseCase(vehicles);
    }

    @Bean
    CheckInUseCase checkInUseCase(VehicleOwnerDirectory vehicles, ParkingSessionRepository sessions,
                                  WalletRepository wallets, FeePolicy feePolicy,
                                  TransactionRunner transactions, EventPublisher events, Clock clock) {
        return new CheckInUseCase(vehicles, sessions, wallets, feePolicy, transactions, events, clock);
    }

    @Bean
    CheckOutUseCase checkOutUseCase(ParkingSessionRepository sessions, VehicleOwnerDirectory vehicles,
                                    PayParkingFeeUseCase payParkingFee, IdempotencyGuard idempotency,
                                    FeePolicy feePolicy, WalletRepository wallets, TransactionRunner transactions,
                                    EventPublisher events, Clock clock) {
        return new CheckOutUseCase(sessions, vehicles, payParkingFee, idempotency, feePolicy, wallets, transactions,
                events, clock);
    }

    @Bean
    PayParkingFeeByVietQrUseCase payParkingFeeByVietQrUseCase(
            ParkingSessionRepository sessions, VehicleOwnerDirectory vehicles,
            WalletRepository wallets, ReceivingWalletProvider receivingWallet,
            PointTransactionRepository pointTransactions, ExchangeRateRepository exchangeRates,
            IdempotencyGuard idempotency, FeePolicy feePolicy, Clock clock) {
        return new PayParkingFeeByVietQrUseCase(sessions, vehicles, wallets, receivingWallet,
                pointTransactions, exchangeRates, idempotency, feePolicy, clock);
    }

    @Bean
    CheckOutByCashUseCase checkOutByCashUseCase(
            ParkingSessionRepository sessions, VehicleOwnerDirectory vehicles,
            GateCashRepository gateCash, OpenShiftLookup openShiftLookup,
            ExchangeRateRepository exchangeRates, FeePolicy feePolicy,
            TransactionRunner transactions, Clock clock) {
        return new CheckOutByCashUseCase(sessions, vehicles, gateCash, openShiftLookup,
                exchangeRates, feePolicy, transactions, clock);
    }
}
