package com.ptit.parking.notification.config;

import com.ptit.parking.notification.application.MyNotificationsUseCase;
import com.ptit.parking.notification.application.port.out.NotificationRepository;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition Root của module notification (Đợt 5). */
@Configuration
public class NotificationConfig {

    @Bean
    MyNotificationsUseCase myNotificationsUseCase(NotificationRepository notifications, Clock clock) {
        return new MyNotificationsUseCase(notifications, clock);
    }
}
