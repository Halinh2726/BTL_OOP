package com.ptit.parking.account.application.usecase;

import com.ptit.parking.account.application.port.out.CredentialRepository;
import com.ptit.parking.account.domain.AccountCredential;
import com.ptit.parking.account.domain.EmailAddress;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.util.Locale;
import java.util.Optional;

/** Phân giải định danh khôi phục mật khẩu (email, tên đăng nhập hoặc MSSV) thành tài khoản hợp lệ. */
final class PasswordResetLookup {

    record EmailTarget(AccountCredential credential, EmailAddress email) {}

    private PasswordResetLookup() {}

    static Optional<AccountCredential> findActiveCredential(CredentialRepository credentials, String rawIdentifier) {
        String trimmed = normalize(rawIdentifier);
        if (trimmed.contains("@")) {
            return credentials.findByEmail(EmailAddress.of(trimmed)).filter(c -> !c.isLocked());
        }
        return findByAccountKey(credentials, trimmed);
    }

    static Optional<EmailTarget> resolveEmailTarget(CredentialRepository credentials, String rawIdentifier) {
        String trimmed = normalize(rawIdentifier);
        if (trimmed.contains("@")) {
            EmailAddress email = EmailAddress.of(trimmed);
            return credentials.findByEmail(email)
                    .filter(c -> !c.isLocked())
                    .map(c -> new EmailTarget(c, email));
        }
        Optional<AccountCredential> account = findByAccountKey(credentials, trimmed);
        if (account.isEmpty()) {
            return Optional.empty();
        }
        return credentials.findEmailByUserId(account.get().userId())
                .map(email -> new EmailTarget(account.get(), email));
    }

    private static String normalize(String rawIdentifier) {
        String trimmed = rawIdentifier == null ? "" : rawIdentifier.strip();
        if (trimmed.isEmpty() || trimmed.length() > EmailAddress.MAX_LENGTH) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR);
        }
        return trimmed;
    }

    private static Optional<AccountCredential> findByAccountKey(CredentialRepository credentials, String key) {
        return credentials.findByUsername(key.toLowerCase(Locale.ROOT))
                .or(() -> credentials.findByStudentCode(key.toUpperCase(Locale.ROOT)))
                .filter(c -> !c.isLocked());
    }
}
