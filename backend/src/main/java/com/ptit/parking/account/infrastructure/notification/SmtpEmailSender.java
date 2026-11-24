package com.ptit.parking.account.infrastructure.notification;

import com.ptit.parking.account.application.port.out.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Adapter gửi email qua SMTP khi máy chủ cấu hình {@code spring.mail.host} và có {@link JavaMailSender}.
 */
class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    SmtpEmailSender(JavaMailSender mailSender, String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(EmailMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(fromAddress);
        mail.setTo(message.to().value());
        mail.setSubject(message.subject());
        mail.setText(message.body());
        mailSender.send(mail);
        log.info("Đã gửi email SMTP tới {}", message.to().masked());
    }
}
