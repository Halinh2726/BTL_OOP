package com.ptit.parking.notification.infrastructure.event;

import com.ptit.parking.account.application.port.out.EmailSender;
import com.ptit.parking.account.application.port.out.EmailSender.EmailMessage;
import com.ptit.parking.account.domain.EmailAddress;
import com.ptit.parking.notification.application.port.out.AlertEmailProvider;
import com.ptit.parking.notification.application.port.out.NotificationRepository;
import com.ptit.parking.shared.application.event.NotifiableEvent;
import com.ptit.parking.shared.application.event.NotifiableEvent.Notice;
import com.ptit.parking.shared.application.event.NotifiableEvent.Role;
import com.ptit.parking.shared.domain.DomainException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Observer gửi email cảnh báo khi có sự cố ({@code SU_CO}) hoặc khiếu nại / bồi thường ({@code KHIEU_NAI},
 * {@code CAMERA}, {@code BOI_THUONG}) sau khi transaction commit thành công.
 * <p>Không bao giờ làm hỏng nghiệp vụ chính nếu SMTP lỗi hoặc chậm.
 */
@Component
public class IncidentEmailNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(IncidentEmailNotificationListener.class);
    private static final Set<String> EMAIL_KINDS = Set.of("SU_CO", "KHIEU_NAI", "CAMERA", "BOI_THUONG");

    private final NotificationRepository notifications;
    private final EmailSender emailSender;
    private final AlertEmailProvider alertEmails;

    public IncidentEmailNotificationListener(NotificationRepository notifications,
                                             EmailSender emailSender,
                                             AlertEmailProvider alertEmails) {
        this.notifications = notifications;
        this.emailSender = emailSender;
        this.alertEmails = alertEmails;
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void on(NotifiableEvent event) {
        List<Notice> eligible = event.notices().stream()
                .filter(n -> EMAIL_KINDS.contains(n.kind()))
                .toList();
        if (eligible.isEmpty()) {
            return;
        }
        boolean asyncSmtp = alertEmails.isSmtpActive();
        for (Notice notice : eligible) {
            Set<EmailAddress> targets = resolveRecipients(notice);
            if (targets.isEmpty()) {
                continue;
            }
            String subject = "[Bãi xe PTIT] " + notice.title();
            String body = notice.body() + "\n\n— Hệ thống quản lý bãi gửi xe PTIT";
            for (EmailAddress target : targets) {
                EmailMessage msg = new EmailMessage(target, subject, body);
                if (asyncSmtp) {
                    CompletableFuture.runAsync(() -> sendSafely(msg));
                } else {
                    sendSafely(msg);
                }
            }
        }
    }

    private Set<EmailAddress> resolveRecipients(Notice notice) {
        Set<EmailAddress> addresses = new LinkedHashSet<>();
        for (String raw : notifications.recipientEmails(notice.to())) {
            try {
                EmailAddress.ofNullable(raw).ifPresent(addresses::add);
            } catch (DomainException ignored) {
                // Bỏ qua địa chỉ email không hợp lệ trong dữ liệu cũ
            }
        }
        if (notice.to() instanceof Role role && "QUAN_LY".equals(role.roleCode())) {
            alertEmails.alertEmail().ifPresent(addresses::add);
        }
        return addresses;
    }

    private void sendSafely(EmailMessage msg) {
        try {
            emailSender.send(msg);
        } catch (RuntimeException ex) {
            log.warn("Không gửi được email cảnh báo tới {}: {}", msg.to().masked(), ex.getMessage());
        }
    }
}
