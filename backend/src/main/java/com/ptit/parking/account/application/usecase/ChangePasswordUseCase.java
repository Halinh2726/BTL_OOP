package com.ptit.parking.account.application.usecase;

import com.ptit.parking.account.application.port.out.CredentialRepository;
import com.ptit.parking.account.domain.AccountCredential;
import com.ptit.parking.auth.domain.PasswordHasher;
import com.ptit.parking.shared.application.port.out.AuditTrail;
import com.ptit.parking.shared.application.port.out.AuditTrail.AuditAction;
import com.ptit.parking.shared.application.port.out.AuditTrail.AuditEntry;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;

/**
 * Đổi mật khẩu khi đã đăng nhập: mật khẩu hiện tại + mật khẩu mới. Cũng là bước bắt buộc sau khi nhận mật khẩu tạm.
 * <p>Trả về thời điểm đổi: controller ghi mốc này vào phiên HIỆN TẠI để phiên này dùng tiếp, còn mọi phiên khác
 * (đăng nhập trước thời điểm đổi) bị CredentialFreshnessFilter đăng xuất ở request kế tiếp.
 */
public class ChangePasswordUseCase {

    public record Command(long userId, String currentPassword, String newPassword) {}

    private final CredentialRepository credentials;
    private final PasswordHasher hasher;
    private final AuditTrail audit;
    private final TransactionRunner transactions;
    private final Clock clock;

    public ChangePasswordUseCase(CredentialRepository credentials, PasswordHasher hasher, AuditTrail audit,
                                 TransactionRunner transactions, Clock clock) {
        this.credentials = credentials;
        this.hasher = hasher;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
    }

    public Instant change(Command command) {
        return transactions.inTransaction(() -> {
            AccountCredential credential = credentials.findByUserId(command.userId())
                    .orElseThrow(() -> new DomainException(ErrorCode.UNAUTHENTICATED));
            credential.change(command.currentPassword(), command.newPassword(), hasher, clock.instant());
            credentials.save(credential);
            audit.record(new AuditEntry(command.userId(), AuditAction.DOI_MAT_KHAU, command.userId(),
                    "Tự đổi mật khẩu", credential.passwordChangedAt()));
            return credential.passwordChangedAt();
        });
    }
}
