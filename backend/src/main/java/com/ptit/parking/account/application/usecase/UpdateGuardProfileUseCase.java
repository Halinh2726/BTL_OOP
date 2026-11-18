package com.ptit.parking.account.application.usecase;

import com.ptit.parking.account.application.port.out.AccountProfileRepository;
import com.ptit.parking.account.domain.GuardProfile;
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
 * Quản lý cập nhật họ tên, số điện thoại của một bảo vệ. Email do bảo vệ tự khai nên giữ nguyên.
 * Pattern matching {@code instanceof GuardProfile}: id không phải bảo vệ → GUARD_NOT_FOUND, không sửa nhầm vai trò khác.
 */
public class UpdateGuardProfileUseCase {

    public record Command(long managerId, long guardId, String fullName, String phone) {}

    private final AccountProfileRepository profiles;
    private final AuditTrail audit;
    private final TransactionRunner transactions;
    private final Clock clock;

    public UpdateGuardProfileUseCase(AccountProfileRepository profiles, AuditTrail audit,
                                     TransactionRunner transactions, Clock clock) {
        this.profiles = profiles;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
    }

    public GuardProfile update(Command command) {
        PhoneNumber phone = PhoneNumber.of(command.phone());
        return transactions.inTransaction(() -> {
            if (!(profiles.findById(command.guardId()).orElse(null) instanceof GuardProfile guard)) {
                throw new DomainException(ErrorCode.GUARD_NOT_FOUND);
            }
            List<String> changed = guard.update(command.fullName(), guard.email().orElse(null), phone);
            if (!changed.isEmpty()) {
                profiles.save(guard);
                audit.record(new AuditEntry(command.managerId(), AuditAction.CAP_NHAT_BAO_VE, guard.userId(),
                        "Cập nhật " + String.join(", ", changed) + " của " + guard.username(), clock.instant()));
            }
            return guard;
        });
    }
}
