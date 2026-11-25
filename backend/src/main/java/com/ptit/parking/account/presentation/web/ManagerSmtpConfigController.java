package com.ptit.parking.account.presentation.web;

import com.ptit.parking.account.application.usecase.ManageSmtpConfigUseCase;
import com.ptit.parking.account.application.usecase.ManageSmtpConfigUseCase.SmtpConfigView;
import com.ptit.parking.account.application.usecase.ManageSmtpConfigUseCase.UpdateCommand;
import com.ptit.parking.shared.infrastructure.security.SessionUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quản lý cấu hình gửi email SMTP động (quyền CASH_DEPOSIT): bật/tắt SMTP, nhập tài khoản Gmail + App Password,
 * cấu hình email nhận cảnh báo sự cố/khiếu nại và gửi email thử.
 */
@RestController
@RequestMapping("/api/v1/manager/smtp-config")
class ManagerSmtpConfigController {

    record SmtpConfigResponse(
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
            String source,
            String updatedAt,
            String envDefaultHost,
            String envDefaultUsername) {

        static SmtpConfigResponse from(SmtpConfigView v, ZoneId zone) {
            return new SmtpConfigResponse(
                    v.enabled(),
                    v.active(),
                    v.host(),
                    v.port(),
                    v.username(),
                    v.hasPassword(),
                    v.fromAddress(),
                    v.starttls(),
                    v.sslEnabled(),
                    v.alertEmail(),
                    v.source().name(),
                    formatTime(v.updatedAt(), zone),
                    v.envDefaultHost(),
                    v.envDefaultUsername());
        }

        private static String formatTime(Instant instant, ZoneId zone) {
            if (instant == null) return null;
            return OffsetDateTime.ofInstant(instant, zone).truncatedTo(ChronoUnit.SECONDS)
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
    }

    record UpdateSmtpConfigRequest(
            @NotNull(message = "Vui lòng chọn trạng thái bật/tắt SMTP")
            Boolean enabled,

            @Size(max = 150, message = "Máy chủ SMTP tối đa 150 ký tự")
            String host,

            @NotNull(message = "Vui lòng nhập cổng SMTP")
            @Min(value = 1, message = "Cổng SMTP tối thiểu 1")
            @Max(value = 65535, message = "Cổng SMTP tối đa 65535")
            Integer port,

            @Size(max = 150, message = "Tài khoản SMTP tối đa 150 ký tự")
            String username,

            @Size(max = 255, message = "Mật khẩu SMTP tối đa 255 ký tự")
            String password,

            @Size(max = 150, message = "Email người gửi tối đa 150 ký tự")
            String fromAddress,

            @NotNull(message = "Vui lòng chọn STARTTLS")
            Boolean starttls,

            @NotNull(message = "Vui lòng chọn SSL")
            Boolean sslEnabled,

            @Size(max = 150, message = "Email nhận cảnh báo tối đa 150 ký tự")
            String alertEmail) {}

    record TestSmtpRequest(
            @Size(max = 150, message = "Email nhận thử tối đa 150 ký tự")
            String toEmail) {}

    record TestSmtpResponse(String message, String toEmail) {}

    private final ManageSmtpConfigUseCase smtpConfig;
    private final ZoneId zone;

    ManagerSmtpConfigController(ManageSmtpConfigUseCase smtpConfig, Clock clock) {
        this.smtpConfig = smtpConfig;
        this.zone = clock.getZone();
    }

    @GetMapping
    SmtpConfigResponse view() {
        return SmtpConfigResponse.from(smtpConfig.view(), zone);
    }

    @PutMapping
    SmtpConfigResponse update(@AuthenticationPrincipal SessionUser manager,
                              @Valid @RequestBody UpdateSmtpConfigRequest body) {
        SmtpConfigView updated = smtpConfig.update(new UpdateCommand(
                body.enabled(),
                body.host(),
                body.port(),
                body.username(),
                body.password(),
                body.fromAddress(),
                body.starttls(),
                body.sslEnabled(),
                body.alertEmail()), manager.id());
        return SmtpConfigResponse.from(updated, zone);
    }

    @DeleteMapping
    SmtpConfigResponse reset() {
        return SmtpConfigResponse.from(smtpConfig.resetToDefault(), zone);
    }

    @PostMapping("/test")
    TestSmtpResponse sendTest(@Valid @RequestBody(required = false) TestSmtpRequest body) {
        String recipient = smtpConfig.sendTestEmail(body == null ? null : body.toEmail());
        return new TestSmtpResponse("Đã gửi email kiểm tra thành công tới " + recipient + ".", recipient);
    }
}
