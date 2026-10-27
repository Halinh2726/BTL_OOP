package com.ptit.parking.parking.config;

import com.ptit.parking.catalog.application.port.out.FeeRuleRepository;
import com.ptit.parking.parking.domain.FeePolicy;
import com.ptit.parking.parking.domain.TimeSlotFeePolicy;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}