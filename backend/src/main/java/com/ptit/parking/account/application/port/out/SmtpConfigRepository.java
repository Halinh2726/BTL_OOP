package com.ptit.parking.account.application.port.out;

import java.time.Instant;
import java.util.Optional;

/** Port lưu cấu hình SMTP gửi email tùy chỉnh bởi Quản lý (tối đa 1 dòng trong smtp_config). */
public interface SmtpConfigRepository {

    record SmtpSettings(
            boolean enabled,
            String host,
            int port,
            String username,
            String password,
            String fromAddress,
            boolean starttls,
            boolean sslEnabled,
            String alertEmail) {}

    record SavedSmtpConfig(SmtpSettings settings, Instant updatedAt) {}

    Optional<SavedSmtpConfig> findSaved();

    SavedSmtpConfig save(SmtpSettings settings, long updatedByUserId);

    void deleteSaved();
}
