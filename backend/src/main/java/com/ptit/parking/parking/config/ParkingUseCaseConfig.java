package com.ptit.parking.parking.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.ptit.parking.parking.application.usecase.CheckOutUseCase;
import com.ptit.parking.payment.application.IdempotencyGuard;
import com.ptit.parking.payment.application.usecase.PayParkingFeeUseCase;
import com.ptit.parking.parking.application.usecase.SuggestPlatesUseCase;

import com.ptit.parking.catalog.application.port.out.FeeRuleRepository;
import com.ptit.parking.parking.application.port.out.ParkingSessionRepository;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory;
import com.ptit.parking.parking.application.usecase.CheckInUseCase;
import com.ptit.parking.parking.application.usecase.PreviewCheckInUseCase;
import com.ptit.parking.parking.application.usecase.PreviewCheckOutUseCase;
import com.ptit.parking.parking.domain.FeePolicy;
import com.ptit.parking.parking.domain.TimeSlotFeePolicy;
import com.ptit.parking.payment.application.port.out.WalletRepository;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
/**
 * Composition Root của module parking. Đây là nơi DUY NHẤT biết chính sách giá đang dùng là
 * {@link TimeSlotFeePolicy}; đổi chính sách khác chỉ cần đổi một dòng ở đây.
 */
@Configuration
public class ParkingUseCaseConfig {

    /**
     * Bảng giá là cấu hình cố định (không có giao diện sửa — Đặc tả yêu cầu mục 3) nên đọc MỘT lần
     * khi khởi động. Bảng giá thiếu khung 00:00 thì ứng dụng không khởi động được — lỗi lộ ra ngay, không âm thầm tính sai.
     */
    @Bean
    FeePolicy feePolicy(FeeRuleRepository feeRules, Clock clock) {
        return new TimeSlotFeePolicy(feeRules.allFeeRules(), clock.getZone());
    }

    @Bean
    PreviewCheckInUseCase previewCheckInUseCase(VehicleOwnerDirectory vehicles, ParkingSessionRepository sessions,
                                                WalletRepository wallets, FeePolicy feePolicy) {
        return new PreviewCheckInUseCase(vehicles, sessions, wallets, feePolicy);
    }

    
    @Bean
    PreviewCheckOutUseCase previewCheckOutUseCase(VehicleOwnerDirectory vehicles, ParkingSessionRepository sessions,
                                                  WalletRepository wallets, FeePolicy feePolicy, Clock clock) {
        return new PreviewCheckOutUseCase(vehicles, sessions, wallets, feePolicy, clock);
    }

        @Bean
    SuggestPlatesUseCase suggestPlatesUseCase(VehicleOwnerDirectory vehicles) {
        return new SuggestPlatesUseCase(vehicles);
    }

    @Bean
    CheckInUseCase checkInUseCase(VehicleOwnerDirectory vehicles, ParkingSessionRepository sessions,
                                  WalletRepository wallets, FeePolicy feePolicy,
                                  TransactionRunner transactions, Clock clock) {
        return new CheckInUseCase(vehicles, sessions, wallets, feePolicy, transactions, clock);
    }
    
    @Bean
    CheckOutUseCase checkOutUseCase(ParkingSessionRepository sessions, VehicleOwnerDirectory vehicles,
                                    PayParkingFeeUseCase payParkingFee, IdempotencyGuard idempotency,
                                    FeePolicy feePolicy, Clock clock) {
        return new CheckOutUseCase(sessions, vehicles, payParkingFee, idempotency, feePolicy, clock);
    }

}