package com.ptit.parking.account.infrastructure.notification;

import com.ptit.parking.account.application.port.out.EmailSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/** Chọn SMTP nếu đã cấu hình {@code spring.mail.host}, ngược lại ghi log cho môi trường demo. */
@Configuration
class EmailSenderConfig {

    @Bean
    EmailSender emailSender(ObjectProvider<JavaMailSender> mailSenders,
                            @Value("${spring.mail.host:}") String mailHost,
                            @Value("${parking.mail.from:no-reply@parking.ptit.edu.vn}") String fromAddress) {
        JavaMailSender mailSender = mailSenders.getIfAvailable();
        if (!mailHost.isBlank() && mailSender != null) {
            return new SmtpEmailSender(mailSender, fromAddress);
        }
        return new LoggingEmailSender();
    }
}
