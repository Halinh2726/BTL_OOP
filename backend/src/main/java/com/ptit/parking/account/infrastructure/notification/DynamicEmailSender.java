package com.ptit.parking.account.infrastructure.notification;

import com.ptit.parking.account.application.port.out.EmailSender;
import com.ptit.parking.account.application.port.out.SmtpConfigRepository;
import com.ptit.parking.account.application.port.out.SmtpConfigRepository.SavedSmtpConfig;
import com.ptit.parking.account.application.port.out.SmtpConfigRepository.SmtpSettings;
import com.ptit.parking.account.application.port.out.SmtpMailTransport;
import com.ptit.parking.account.application.usecase.ManageSmtpConfigUseCase.EnvDefaults;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Adapter gửi email động: đọc cấu hình từ bảng {@code smtp_config} (nếu Quản lý đã cấu hình) hoặc từ biến môi trường.
 * Khi SMTP chưa bật hoặc thiếu thông tin kết nối, tự động rơi về chế độ ghi log demo để không làm gián đoạn hệ thống.
 */
public class DynamicEmailSender implements EmailSender, SmtpMailTransport {

    private static final Logger log = LoggerFactory.getLogger(DynamicEmailSender.class);

    private final SmtpConfigRepository repository;
    private final EnvDefaults envDefaults;

    public DynamicEmailSender(SmtpConfigRepository repository, EnvDefaults envDefaults) {
        this.repository = repository;
        this.envDefaults = envDefaults;
    }

    @Override
    public void send(EmailMessage message) {
        SmtpSettings settings = currentSettings();
        if (!isUsable(settings)) {
            log.info("[EMAIL] Tới: {} | Tiêu đề: {}\n{}", message.to(), message.subject(), message.body());
            return;
        }
        sendWithSettings(settings, message);
    }

    @Override
    public void sendDirect(EmailMessage message) {
        SmtpSettings settings = currentSettings();
        if (!isUsable(settings)) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR,
                    "Chưa cấu hình đầy đủ máy chủ / tài khoản / mật khẩu SMTP.");
        }
        try {
            sendWithSettings(settings, message);
        } catch (MailAuthenticationException ex) {
            log.warn("Xác thực SMTP thất bại tới {}: {}", settings.host(), ex.getMessage());
            throw new DomainException(ErrorCode.VALIDATION_ERROR,
                    "Xác thực SMTP thất bại. Với Gmail, hãy bật Xác minh 2 bước và dùng Mật khẩu ứng dụng (App Password 16 ký tự).");
        } catch (MailException ex) {
            String detail = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
            log.warn("Gửi email SMTP thất bại qua {}:{} — {}", settings.host(), settings.port(), detail);
            throw new DomainException(ErrorCode.VALIDATION_ERROR,
                    "Không gửi được email qua SMTP (" + settings.host() + ":" + settings.port() + "): " + detail);
        }
    }

    private SmtpSettings currentSettings() {
        return repository.findSaved()
                .map(SavedSmtpConfig::settings)
                .orElseGet(envDefaults::toSettings);
    }

    private static boolean isUsable(SmtpSettings s) {
        return s.enabled()
                && s.host() != null && !s.host().isBlank()
                && s.username() != null && !s.username().isBlank()
                && s.password() != null && !s.password().isBlank();
    }

    private static void sendWithSettings(SmtpSettings settings, EmailMessage message) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(settings.host());
        sender.setPort(settings.port());
        sender.setUsername(settings.username());
        sender.setPassword(settings.password());
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", String.valueOf(settings.starttls()));
        props.put("mail.smtp.ssl.enable", String.valueOf(settings.sslEnabled()));
        props.put("mail.smtp.connectiontimeout", "8000");
        props.put("mail.smtp.timeout", "8000");
        props.put("mail.smtp.writetimeout", "8000");

        SimpleMailMessage mail = new SimpleMailMessage();
        String from = (settings.fromAddress() != null && !settings.fromAddress().isBlank())
                ? settings.fromAddress()
                : settings.username();
        mail.setFrom(from);
        mail.setTo(message.to().value());
        mail.setSubject(message.subject());
        mail.setText(message.body());

        sender.send(mail);
        log.info("Đã gửi email SMTP tới {}", message.to().masked());
    }
}
