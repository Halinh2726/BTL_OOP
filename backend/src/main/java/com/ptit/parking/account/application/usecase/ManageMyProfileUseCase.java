package com.ptit.parking.account.application.usecase;

import com.ptit.parking.account.application.port.out.AccountProfileRepository;
import com.ptit.parking.account.domain.AccountProfile;
import com.ptit.parking.account.domain.EmailAddress;
import com.ptit.parking.account.domain.PhoneNumber;
import com.ptit.parking.shared.application.port.out.AuditTrail;
import com.ptit.parking.shared.application.port.out.AuditTrail.AuditAction;
import com.ptit.parking.shared.application.port.out.AuditTrail.AuditEntry;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Clock;
import java.util.List;

/**
 * Xem / cập nhật thông tin cá nhân của CHÍNH MÌNH (mọi vai trò). {@code userId} luôn lấy từ phiên.
 * Sửa được: họ tên, email, số điện thoại. Không sửa được: tên đăng nhập, MSSV (không có trong Command).
 * Việc "trường nào bắt buộc với vai trò nào" do lớp con của {@link AccountProfile} quyết định — use case không if/else theo vai trò.
 */
public class ManageMyProfileUseCase {

    public record UpdateCommand(long userId, String fullName, String email, String phone) {}

    private final AccountProfileRepository profiles;
    private final AuditTrail audit;
    private final TransactionRunner transactions;
    private final Clock clock;

    public ManageMyProfileUseCase(AccountProfileRepository profiles, AuditTrail audit,
                                  TransactionRunner transactions, Clock clock) {
        this.profiles = profiles;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
    }

    public AccountProfile get(long userId) {
        return profiles.findById(userId).orElseThrow(() -> new DomainException(ErrorCode.UNAUTHENTICATED));
    }

    public AccountProfile update(UpdateCommand command) {
        EmailAddress email = EmailAddress.ofNullable(command.email()).orElse(null);
        PhoneNumber phone = PhoneNumber.ofNullable(command.phone()).orElse(null);

        return transactions.inTransaction(() -> {
            AccountProfile profile = get(command.userId());
            if (email != null && profiles.emailUsedByOther(email, command.userId())) {
                throw new DomainException(ErrorCode.EMAIL_EXISTS);
            }
            List<String> changed = profile.update(command.fullName(), email, phone);
            if (!changed.isEmpty()) {
                // Hai người cùng lưu một email cùng lúc: UNIQUE uq_users_email chặn người sau -> EMAIL_EXISTS
                profiles.save(profile);
                audit.record(new AuditEntry(command.userId(), AuditAction.CAP_NHAT_THONG_TIN, command.userId(),
                        "Tự cập nhật " + String.join(", ", changed), clock.instant()));
            }
            return profile;
        });
    }
}
