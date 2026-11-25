package com.ptit.parking.account.application.usecase;

import com.ptit.parking.account.application.event.SecurityChanged;
import com.ptit.parking.account.application.port.out.CredentialRepository;
import com.ptit.parking.account.application.port.out.PasswordResetCodeRepository;
import com.ptit.parking.account.domain.AccountCredential;
import com.ptit.parking.account.domain.PasswordPolicy;
import com.ptit.parking.account.domain.PasswordResetCode;
import com.ptit.parking.auth.domain.PasswordHasher;
import com.ptit.parking.shared.application.port.out.AuditTrail;
import com.ptit.parking.shared.application.port.out.AuditTrail.AuditAction;
import com.ptit.parking.shared.application.port.out.AuditTrail.AuditEntry;
import com.ptit.parking.shared.application.port.out.EventPublisher;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Quên mật khẩu — bước 2: định danh (email / tên đăng nhập / MSSV) + mã OTP + mật khẩu mới.
 * <p>Thứ tự quan trọng:
 * <ol>
 *   <li>Kiểm tra mật khẩu mới TRƯỚC — gõ mật khẩu quá ngắn không làm mất một lượt thử mã.</li>
 *   <li>Trong MỘT transaction: khóa mã mới nhất, thử mã. Sai thì lưu số lần sai rồi transaction KẾT THÚC BÌNH THƯỜNG
 *       (để lần sai được ghi), sau đó mới báo lỗi. Đúng thì đặt mật khẩu mới và gửi thông báo bảo mật.</li>
 * </ol>
 * Tài khoản không tồn tại cũng báo RESET_CODE_INVALID — không tiết lộ thông tin đã đăng ký.
 */
public class ConfirmPasswordResetUseCase {

    public record Command(String email, String code, String newPassword) {}

    private final CredentialRepository credentials;
    private final PasswordResetCodeRepository codes;
    private final PasswordHasher hasher;
    private final AuditTrail audit;
    private final TransactionRunner transactions;
    private final EventPublisher events;
    private final Clock clock;

    public ConfirmPasswordResetUseCase(CredentialRepository credentials, PasswordResetCodeRepository codes,
                                       PasswordHasher hasher, AuditTrail audit, TransactionRunner transactions,
                                       Clock clock) {
        this(credentials, codes, hasher, audit, transactions, EventPublisher.NONE, clock);
    }

    public ConfirmPasswordResetUseCase(CredentialRepository credentials, PasswordResetCodeRepository codes,
                                       PasswordHasher hasher, AuditTrail audit, TransactionRunner transactions,
                                       EventPublisher events, Clock clock) {
        this.credentials = credentials;
        this.codes = codes;
        this.hasher = hasher;
        this.audit = audit;
        this.transactions = transactions;
        this.events = Objects.requireNonNullElse(events, EventPublisher.NONE);
        this.clock = clock;
    }

    public void confirm(Command command) {
        PasswordPolicy.requireAcceptable(command.newPassword());
        AccountCredential credential = PasswordResetLookup.findActiveCredential(credentials, command.email())
                .orElseThrow(() -> new DomainException(ErrorCode.RESET_CODE_INVALID));

        boolean accepted = transactions.inTransaction(() -> {
            Instant now = clock.instant();
            PasswordResetCode code = codes.lockLatest(credential.userId())
                    .orElseThrow(() -> new DomainException(ErrorCode.RESET_CODE_INVALID));
            boolean ok = code.verify(command.code(), hasher, now);
            codes.save(code);
            if (ok) {
                // Mật khẩu mới trùng mật khẩu cũ -> PASSWORD_REUSED, rollback: mã chưa bị tính là đã dùng
                credential.reset(command.newPassword(), hasher, now);
                credentials.save(credential);
                audit.record(new AuditEntry(credential.userId(), AuditAction.KHOI_PHUC_MAT_KHAU, credential.userId(),
                        "Đặt lại mật khẩu bằng mã xác nhận email", now));
                events.publish(new SecurityChanged(
                        credential.userId(),
                        "Mật khẩu đã được khôi phục",
                        "Mật khẩu tài khoản vừa được đặt lại qua mã xác nhận email. Các phiên đăng nhập cũ đã tự động bị đăng xuất."));
            }
            return ok;
        });
        if (!accepted) {
            throw new DomainException(ErrorCode.RESET_CODE_INVALID);
        }
    }
}
