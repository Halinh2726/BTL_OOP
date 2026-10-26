package com.ptit.parking.shared.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Đồng hồ DUY NHẤT của hệ thống, theo giờ Việt Nam (R03: giờ tính phí là giờ máy chủ).
 * Mọi nơi cần "bây giờ" đều nhận Clock qua constructor thay vì gọi Instant.now() trực tiếp:
 * test có thể thay bằng đồng hồ cố định (ví dụ đúng 17:59 hay 18:00).
 */
@Configuration
public class TimeConfig {

    @Bean
    Clock clock(@Value("${parking.zone-id}") String zoneId) {
        return Clock.system(ZoneId.of(zoneId));
    }
}
