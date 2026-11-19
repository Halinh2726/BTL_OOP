package com.ptit.parking.manager.application.usecase;

import com.ptit.parking.auth.domain.Guard;
import com.ptit.parking.auth.domain.PasswordHasher;
import com.ptit.parking.manager.application.port.out.GuardAccountRepository;
import com.ptit.parking.shared.application.port.out.AuditTrail;
import com.ptit.parking.shared.application.port.out.AuditTrail.AuditAction;
import com.ptit.parking.shared.application.port.out.AuditTrail.AuditEntry;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Clock;

/**
 * QL03 (Đặc tả API #21) — quản lý tạo tài khoản bảo vệ: vai trò BAO_VE, trạng thái HOAT_DONG, mật khẩu băm BCrypt.
 * Một transaction ghi users + guards. Bảo vệ không tự đăng ký được — chỉ quản lý (quyền GUARD_CREATE) mới tạo.
 * <p>Đợt 4a: ghi nhật ký TAO_BAO_VE (ai tạo, lúc nào) trong CÙNG transaction — tạo thất bại thì không có dòng nhật ký.
 */
public class CreateGuardUseCase {

    /** {@code managerId}: quản lý đang đăng nhập — người chịu trách nhiệm, ghi vào nhật ký. */
    public record Command(long managerId, String fullName, String phone, String username, String password) {}

    private final GuardAccountRepository guards;
    private final PasswordHasher hasher;
    private final TransactionRunner transactions;
    private final AuditTrail audit;
    private final Clock clock;

    public CreateGuardUseCase(GuardAccountRepository guards, PasswordHasher hasher, TransactionRunner transactions,
                              AuditTrail audit, Clock clock) {
        this.guards = guards;
        this.hasher = hasher;
        this.transactions = transactions;
        this.audit = audit;
        this.clock = clock;
    }

    public Guard create(Command command) {
        // Băm BCrypt (chậm, cố ý) TRƯỚC khi mở transaction — giống đăng ký sinh viên
        Guard guard = Guard.create(command.username(), hasher.hash(command.password()),
                command.fullName(), command.phone());

        return transactions.inTransaction(() -> {
            if (guards.usernameExists(guard.username())) {
                throw new DomainException(ErrorCode.USERNAME_EXISTS);
            }
            // Hai quản lý tạo cùng username cùng lúc: UNIQUE uq_users_username chặn người thứ hai -> USERNAME_EXISTS
            Guard saved = guards.save(guard);
            audit.record(new AuditEntry(command.managerId(), AuditAction.TAO_BAO_VE, saved.id(),
                    "Tạo tài khoản bảo vệ " + saved.username(), clock.instant()));
            return saved;
        });
    }
}