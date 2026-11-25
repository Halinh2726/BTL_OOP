package com.ptit.parking.account.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.account.AccountFakes.Outbox;
import com.ptit.parking.account.application.port.out.EmailSender.EmailMessage;
import com.ptit.parking.account.application.port.out.SmtpConfigRepository;
import com.ptit.parking.account.application.port.out.SmtpMailTransport;
import com.ptit.parking.account.application.usecase.ManageSmtpConfigUseCase.EnvDefaults;
import com.ptit.parking.account.application.usecase.ManageSmtpConfigUseCase.Source;
import com.ptit.parking.account.application.usecase.ManageSmtpConfigUseCase.UpdateCommand;
import com.ptit.parking.account.domain.EmailAddress;
import com.ptit.parking.gate.application.event.IncidentChanged;
import com.ptit.parking.gate.domain.IncidentStatus;
import com.ptit.parking.gate.domain.IncidentType;
import com.ptit.parking.notification.application.port.out.AlertEmailProvider;
import com.ptit.parking.notification.application.port.out.NotificationRepository;
import com.ptit.parking.notification.infrastructure.event.IncidentEmailNotificationListener;
import com.ptit.parking.parking.application.event.LowBalanceWarning;
import com.ptit.parking.shared.application.event.NotifiableEvent.Notice;
import com.ptit.parking.shared.application.event.NotifiableEvent.Recipient;
import com.ptit.parking.shared.application.event.NotifiableEvent.Role;
import com.ptit.parking.shared.domain.DomainException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ManageSmtpConfigUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    @Test
    void luuSmtp_giuMatKhauCuKhiDeTrong_vaGuiEmailThu() {
        AtomicReference<SmtpConfigRepository.SavedSmtpConfig> savedRef = new AtomicReference<>();
        SmtpConfigRepository repo = new SmtpConfigRepository() {
            @Override
            public Optional<SavedSmtpConfig> findSaved() {
                return Optional.ofNullable(savedRef.get());
            }

            @Override
            public SavedSmtpConfig save(SmtpSettings settings, long updatedByUserId) {
                SavedSmtpConfig saved = new SavedSmtpConfig(settings, CLOCK.instant());
                savedRef.set(saved);
                return saved;
            }

            @Override
            public void deleteSaved() {
                savedRef.set(null);
            }
        };
        Outbox outbox = new Outbox();
        SmtpMailTransport transport = outbox::send;
        EnvDefaults env = new EnvDefaults("smtp.gmail.com", 587, "", "", "no-reply@parking.ptit.edu.vn", true, false, "");

        ManageSmtpConfigUseCase useCase = new ManageSmtpConfigUseCase(repo, env, transport, CLOCK);

        assertThat(useCase.view().source()).isEqualTo(Source.ENV);
        assertThat(useCase.view().active()).isFalse();
        assertThatThrownBy(() -> useCase.sendTestEmail("ql@ptit.edu.vn")).isInstanceOf(DomainException.class);

        var updated = useCase.update(new UpdateCommand(
                true, "smtp.gmail.com", 587, "parking@gmail.com", "abcd efgh ijkl mnop",
                "", true, false, "alert@ptit.edu.vn"), 1L);
        assertThat(updated.source()).isEqualTo(Source.DATABASE);
        assertThat(updated.active()).isTrue();
        assertThat(updated.hasPassword()).isTrue();
        assertThat(updated.fromAddress()).isEqualTo("parking@gmail.com");
        assertThat(updated.alertEmail()).isEqualTo("alert@ptit.edu.vn");

        // Cập nhật không nhập lại password -> vẫn giữ password cũ
        var updated2 = useCase.update(new UpdateCommand(
                true, "smtp.gmail.com", 587, "parking@gmail.com", "",
                "", true, false, "alert2@ptit.edu.vn"), 1L);
        assertThat(updated2.hasPassword()).isTrue();
        assertThat(useCase.sendTestEmail(null)).isEqualTo("alert2@ptit.edu.vn");
        assertThat(outbox.sent).singleElement().satisfies((EmailMessage m) -> {
            assertThat(m.to().value()).isEqualTo("alert2@ptit.edu.vn");
            assertThat(m.subject()).contains("SMTP");
        });
    }

    @Test
    void listenerGuiEmailSuCoChoQuanLyVaAlertEmail_boQuaSuKienKhac() {
        Outbox outbox = new Outbox();
        NotificationRepository notifications = new NotificationRepository() {
            @Override public void deliver(List<Notice> notices, Instant at) {}
            @Override public List<Stored> latest(long userId, int limit) { return List.of(); }
            @Override public long unreadCount(long userId) { return 0; }
            @Override public void markRead(long userId, long notificationId, Instant at) {}
            @Override public void markAllRead(long userId, Instant at) {}

            @Override
            public List<String> recipientEmails(Recipient recipient) {
                if (recipient instanceof Role r && r.roleCode().equals("QUAN_LY")) {
                    return List.of("ql1@ptit.edu.vn", "alert@ptit.edu.vn");
                }
                return List.of("sv@ptit.edu.vn");
            }
        };
        AlertEmailProvider alertProvider = new AlertEmailProvider() {
            @Override public boolean isSmtpActive() { return false; }
            @Override public Optional<EmailAddress> alertEmail() { return Optional.of(EmailAddress.of("alert@ptit.edu.vn")); }
        };

        IncidentEmailNotificationListener listener =
                new IncidentEmailNotificationListener(notifications, outbox, alertProvider);

        listener.on(new LowBalanceWarning(3L, 1000, 3000));
        assertThat(outbox.sent).isEmpty();

        listener.on(new IncidentChanged(99L, 4L, IncidentType.XE_HU_HONG, "29A-123.45",
                "Gãy gương chiếu hậu", IncidentStatus.MOI, null, 7L));

        assertThat(outbox.sent).extracting(m -> m.to().value())
                .containsExactly("ql1@ptit.edu.vn", "alert@ptit.edu.vn", "sv@ptit.edu.vn");
    }
}
