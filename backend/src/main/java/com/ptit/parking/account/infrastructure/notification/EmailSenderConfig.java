package com.ptit.parking.account.infrastructure.notification;

import com.ptit.parking.account.application.port.out.SmtpConfigRepository;
import com.ptit.parking.account.application.usecase.ManageSmtpConfigUseCase.EnvDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Khởi tạo cấu hình mặc định từ môi trường và adapter gửi email động ({@link DynamicEmailSender}). */
@Configuration
public class EmailSenderConfig {

    @Bean
    EnvDefaults smtpEnvDefaults(
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${spring.mail.port:587}") int mailPort,
            @Value("${spring.mail.username:}") String mailUsername,
            @Value("${spring.mail.password:}") String mailPassword,
            @Value("${parking.mail.from:no-reply@parking.ptit.edu.vn}") String fromAddress,
            @Value("${spring.mail.properties.mail.smtp.starttls.enable:true}") boolean starttls,
            @Value("${spring.mail.properties.mail.smtp.ssl.enable:false}") boolean sslEnabled,
            @Value("${parking.mail.alert-to:}") String alertEmail) {
        return new EnvDefaults(mailHost, mailPort, mailUsername, mailPassword,
                fromAddress, starttls, sslEnabled, alertEmail);
    }

    @Bean
    @Primary
    DynamicEmailSender dynamicEmailSender(SmtpConfigRepository repository, EnvDefaults envDefaults) {
        return new DynamicEmailSender(repository, envDefaults);
    }
}
