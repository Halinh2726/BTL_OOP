package com.ptit.parking.account.application.usecase;

import com.ptit.parking.account.application.event.SecurityChanged;
import com.ptit.parking.account.application.port.out.CredentialRepository;
import com.ptit.parking.account.application.port.out.SecretGenerator;
import com.ptit.parking.account.domain.AccountCredential;
import com.ptit.parking.auth.domain.PasswordHasher;
import com.ptit.parking.auth.domain.Role;
import com.ptit.parking.shared.application.port.out.AuditTrail;
import com.ptit.parking.shared.application.port.out.AuditTrail.AuditAction;
import com.ptit.parking.shared.application.port.out.AuditTrail.AuditEntry;
import com.ptit.parking.shared.application.port.out.EventPublisher;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Clock;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Quên mật khẩu — cách 2: sinh viên mang thẻ tới quản lý (hoặc bảo vệ báo quên mật khẩu). Quản lý đối chiếu rồi
 * cấp mật khẩu tạm; lần đăng nhập sau người dùng BẮT BUỘC đổi (CredentialFreshnessFilter chặn mọi API khác).
 * <p>Mật khẩu tạm chỉ hiện MỘT lần trong response cho quản lý đọc cho người dùng; hệ thống chỉ lưu hash.
 * Mọi lần cấp được ghi nhật ký (ai cấp, cho ai, lúc nào).
 */
public class IssueTemporaryPasswordUseCase {

    public record TemporaryPassword(long userId, String username, String fullName, String password) {}

    private final CredentialRepository credentials;
    private final PasswordHasher hasher;
    private final SecretGenerator secrets;
    private final AuditTrail audit;
    private final TransactionRunner transactions;
    private final EventPublisher events;
    private final Clock clock;

    public IssueTemporaryPasswordUseCase(CredentialRepository credentials, PasswordHasher hasher,
                                         SecretGenerator secrets, AuditTrail audit,
                                         TransactionRunner transactions, Clock clock) {
        this(credentials, hasher, secrets, audit, transactions, EventPublisher.NONE, clock);
    }

    public IssueTemporaryPasswordUseCase(CredentialRepository credentials, PasswordHasher hasher,
                                         SecretGenerator secrets, AuditTrail audit,
                                         TransactionRunner transactions, EventPublisher events, Clock clock) {
        this.credentials = credentials;
        this.hasher = hasher;
        this.secrets = secrets;
        this.audit = audit;
        this.transactions = transactions;
        this.events = events;
        this.clock = clock;
    }

    /** Theo MSSV — quản lý vừa đối chiếu thẻ sinh viên. */
    public TemporaryPassword forStudent(long managerId, String studentCode) {
        return issue(managerId, () -> credentials.findByStudentCode(studentCode),
                Role.SINH_VIEN, ErrorCode.STUDENT_NOT_FOUND);
    }

    /** Theo id bảo vệ (danh sách bảo vệ). */
    public TemporaryPassword forGuard(long managerId, long guardId) {
        return issue(managerId, () -> credentials.findByUserId(guardId), Role.BAO_VE, ErrorCode.GUARD_NOT_FOUND);
    }

    private TemporaryPassword issue(long managerId, Supplier<Optional<AccountCredential>> target,
                                    Role expectedRole, ErrorCode notFound) {
        String password = secrets.temporaryPassword();
        return transactions.inTransaction(() -> {
            AccountCredential credential = target.get()
                    .filter(c -> c.role() == expectedRole)
                    .orElseThrow(() -> new DomainException(notFound));
            credential.issueTemporary(password, hasher, clock.instant());
            credentials.save(credential);
            audit.record(new AuditEntry(managerId, AuditAction.CAP_MAT_KHAU_TAM, credential.userId(),
                    "Cấp mật khẩu tạm cho " + credential.username(), credential.passwordChangedAt()));
            events.publish(new SecurityChanged(
                    credential.userId(),
                    "Quản lý vừa cấp mật khẩu tạm cho tài khoản của bạn",
                    "Vui lòng đặt lại mật khẩu cá nhân mới ngay khi đăng nhập."));
            return new TemporaryPassword(credential.userId(), credential.username(), credential.fullName(), password);
        });
    }
}
