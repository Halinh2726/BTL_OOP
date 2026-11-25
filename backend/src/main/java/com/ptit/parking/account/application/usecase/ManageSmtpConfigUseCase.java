package com.ptit.parking.account.application.usecase;

import com.ptit.parking.account.application.port.out.EmailSender.EmailMessage;
import com.ptit.parking.account.application.port.out.SmtpConfigRepository;
import com.ptit.parking.account.application.port.out.SmtpConfigRepository.SavedSmtpConfig;
import com.ptit.parking.account.application.port.out.SmtpConfigRepository.SmtpSettings;
import com.ptit.parking.account.application.port.out.SmtpMailTransport;
import com.ptit.parking.account.domain.EmailAddress;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Quản lý cấu hình gửi email SMTP (Quản lý nhập trực tiếp hoặc dùng mặc định từ .env) và gửi thử email.
 * Mật khẩu SMTP không bao giờ được trả ra API (chỉ trả cờ {@code hasPassword}).
 */
public class ManageSmtpConfigUseCase {

    public enum Source { ENV, DATABASE }

    public record EnvDefaults(
            String host,
            int port,
            String username,
            String password,
            String fromAddress,
            boolean starttls,
            boolean sslEnabled,
            String alertEmail) {

        public SmtpSettings toSettings() {
            String safeHost = trim(host);
            String safeUser = trim(username);
            String safePass = password == null ? "" : password;
            boolean hasAll = !safeHost.isBlank() && !safeUser.isBlank() && !safePass.isBlank();
            return new SmtpSettings(
                    hasAll,
                    safeHost,
                    port > 0 && port <= 65535 ? port : 587,
                    safeUser,
                    safePass,
                    trim(fromAddress).isBlank() ? "no-reply@parking.ptit.edu.vn" : trim(fromAddress),
                    starttls,
                    sslEnabled,
                    emptyToNull(alertEmail));
        }
    }

    public record SmtpConfigView(
            boolean enabled,
            boolean active,
            String host,
            int port,
            String username,
            boolean hasPassword,
            String fromAddress,
            boolean starttls,
            boolean sslEnabled,
            String alertEmail,
            Source source,
            Instant updatedAt,
            String envDefaultHost,
            String envDefaultUsername) {}

    public record UpdateCommand(
            boolean enabled,
            String host,
            int port,
            String username,
            String password,
            String fromAddress,
            boolean starttls,
            boolean sslEnabled,
            String alertEmail) {}

    private final SmtpConfigRepository repository;
    private final EnvDefaults envDefaults;
    private final SmtpMailTransport transport;
    private final Clock clock;

    public ManageSmtpConfigUseCase(SmtpConfigRepository repository, EnvDefaults envDefaults,
                                   SmtpMailTransport transport, Clock clock) {
        this.repository = repository;
        this.envDefaults = envDefaults;
        this.transport = transport;
        this.clock = clock;
    }

    public SmtpConfigView view() {
        Optional<SavedSmtpConfig> saved = repository.findSaved();
        if (saved.isPresent()) {
            return toView(saved.get().settings(), Source.DATABASE, saved.get().updatedAt());
        }
        return toView(envDefaults.toSettings(), Source.ENV, null);
    }

    /** Cấu hình hiệu lực dùng nội bộ để gửi mail (cả trường password thật). */
    public SmtpSettings currentEffective() {
        return repository.findSaved()
                .map(SavedSmtpConfig::settings)
                .orElseGet(envDefaults::toSettings);
    }

    public Optional<EmailAddress> configuredAlertEmail() {
        String raw = currentEffective().alertEmail();
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(EmailAddress.of(raw));
        } catch (DomainException ex) {
            return Optional.empty();
        }
    }

    public boolean isSmtpActive() {
        SmtpSettings s = currentEffective();
        return s.enabled() && !trim(s.host()).isBlank() && !trim(s.username()).isBlank()
                && s.password() != null && !s.password().isBlank();
    }

    public SmtpConfigView update(UpdateCommand command, long managerId) {
        String host = trim(command.host());
        String username = trim(command.username());
        int port = command.port();
        if (port < 1 || port > 65535) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Cổng SMTP phải nằm trong khoảng 1 đến 65535.");
        }
        if (command.enabled() && host.isBlank()) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Vui lòng nhập máy chủ SMTP (VD: smtp.gmail.com).");
        }
        if (command.enabled() && username.isBlank()) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Vui lòng nhập tài khoản / email gửi SMTP.");
        }

        String password = resolvePassword(command.password(), username);
        if (command.enabled() && password.isBlank()) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR,
                    "Vui lòng nhập mật khẩu hoặc App Password cho tài khoản SMTP.");
        }

        String fromAddress = trim(command.fromAddress());
        if (fromAddress.isBlank()) {
            fromAddress = username.contains("@") ? username : envDefaults.toSettings().fromAddress();
        }

        String alertEmail = null;
        if (!trim(command.alertEmail()).isBlank()) {
            try {
                alertEmail = EmailAddress.of(command.alertEmail()).value();
            } catch (DomainException ex) {
                throw new DomainException(ErrorCode.VALIDATION_ERROR, "Email nhận cảnh báo sự cố không hợp lệ.");
            }
        }

        SmtpSettings savedSettings = new SmtpSettings(
                command.enabled(),
                host,
                port,
                username,
                password,
                fromAddress,
                command.starttls(),
                command.sslEnabled(),
                alertEmail);
        SavedSmtpConfig saved = repository.save(savedSettings, managerId);
        return toView(saved.settings(), Source.DATABASE, saved.updatedAt());
    }

    public SmtpConfigView resetToDefault() {
        repository.deleteSaved();
        return view();
    }

    public String sendTestEmail(String rawToEmail) {
        String target = trim(rawToEmail);
        if (target.isBlank()) {
            target = trim(currentEffective().alertEmail());
        }
        if (target.isBlank()) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR,
                    "Vui lòng nhập địa chỉ email nhận thử (hoặc cấu hình Email nhận cảnh báo sự cố).");
        }
        EmailAddress recipient;
        try {
            recipient = EmailAddress.of(target);
        } catch (DomainException ex) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Địa chỉ email nhận thử không hợp lệ.");
        }

        if (!isSmtpActive()) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR,
                    "SMTP chưa được bật hoặc thiếu Host / Tài khoản / App Password. Hãy lưu cấu hình trước khi gửi thử.");
        }

        transport.sendDirect(new EmailMessage(
                recipient,
                "[Bãi xe PTIT] Kiểm tra kết nối gửi email SMTP",
                "Xin chào,\n\nĐây là email kiểm tra từ hệ thống quản lý bãi gửi xe PTIT.\n"
                        + "Cấu hình SMTP của bạn đã hoạt động bình thường (lúc "
                        + clock.instant().atZone(clock.getZone()).toLocalTime().withNano(0) + ").\n\n"
                        + "Hệ thống sẽ tự động gửi mã OTP quên mật khẩu và cảnh báo sự cố / khiếu nại qua cấu hình này."));
        return recipient.value();
    }

    private String resolvePassword(String provided, String newUsername) {
        if (provided != null && !provided.isBlank()) {
            return provided.strip();
        }
        Optional<SavedSmtpConfig> existing = repository.findSaved();
        if (existing.isPresent() && existing.get().settings().password() != null
                && !existing.get().settings().password().isBlank()) {
            return existing.get().settings().password();
        }
        SmtpSettings env = envDefaults.toSettings();
        if (!env.password().isBlank() && newUsername.equalsIgnoreCase(env.username())) {
            return env.password();
        }
        return "";
    }

    private SmtpConfigView toView(SmtpSettings s, Source source, Instant updatedAt) {
        boolean hasPassword = s.password() != null && !s.password().isBlank();
        boolean active = s.enabled() && !trim(s.host()).isBlank() && !trim(s.username()).isBlank() && hasPassword;
        SmtpSettings env = envDefaults.toSettings();
        return new SmtpConfigView(
                s.enabled(),
                active,
                s.host(),
                s.port(),
                s.username(),
                hasPassword,
                s.fromAddress(),
                s.starttls(),
                s.sslEnabled(),
                s.alertEmail(),
                source,
                updatedAt,
                env.host(),
                env.username());
    }

    private static String trim(String value) {
        return value == null ? "" : value.strip();
    }

    private static String emptyToNull(String value) {
        String cleaned = trim(value);
        return cleaned.isBlank() ? null : cleaned;
    }
}
