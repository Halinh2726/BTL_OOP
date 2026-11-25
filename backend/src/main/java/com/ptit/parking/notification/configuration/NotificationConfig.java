package com.ptit.parking.notification.configuration;

import com.ptit.parking.account.application.usecase.ManageSmtpConfigUseCase;
import com.ptit.parking.account.domain.EmailAddress;
import com.ptit.parking.notification.application.MyNotificationsUseCase;
import com.ptit.parking.notification.application.port.out.AlertEmailProvider;
import com.ptit.parking.notification.application.port.out.NotificationRepository;
import java.time.Clock;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition Root của module notification (Đợt 5). */
@Configuration
public class NotificationConfig {

    @Bean
    MyNotificationsUseCase myNotificationsUseCase(NotificationRepository notifications, Clock clock) {
        return new MyNotificationsUseCase(notifications, clock);
    }

    @Bean
    AlertEmailProvider alertEmailProvider(ManageSmtpConfigUseCase smtpConfig) {
        return new AlertEmailProvider() {
            @Override
            public boolean isSmtpActive() {
                return smtpConfig.isSmtpActive();
            }

            @Override
            public Optional<EmailAddress> alertEmail() {
                return smtpConfig.configuredAlertEmail();
            }
        };
    }
}
