package com.ptit.parking.account.application.usecase;

import com.ptit.parking.account.application.port.out.CredentialRepository;
import com.ptit.parking.account.application.port.out.EmailSender;
import com.ptit.parking.account.application.port.out.EmailSender.EmailMessage;
import com.ptit.parking.account.application.port.out.PasswordResetCodeRepository;
import com.ptit.parking.account.application.port.out.SecretGenerator;
import com.ptit.parking.account.application.usecase.PasswordResetLookup.EmailTarget;
import com.ptit.parking.account.domain.EmailAddress;
import com.ptit.parking.account.domain.PasswordResetCode;
import com.ptit.parking.auth.domain.PasswordHasher;
import com.ptit.parking.shared.application.port.out.AuditTrail;
import com.ptit.parking.shared.application.port.out.AuditTrail.AuditAction;
import com.ptit.parking.shared.application.port.out.AuditTrail.AuditEntry;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Quên mật khẩu — bước 1: nhập email đã đăng ký → nhận mã OTP 6 số (hiệu lực 10 phút).
 * <p><b>Không tiết lộ email có tồn tại hay không:</b> email lạ, tài khoản bị khóa, hay vừa gửi mã chưa tới 1 phút
 * đều kết thúc "thành công" giống hệt nhau — kẻ xấu không dùng form này để dò danh sách email được.
 */
public class RequestPasswordResetUseCase {

    private final CredentialRepository credentials;
    private final PasswordResetCodeRepository codes;
    private final PasswordHasher hasher;
    private final SecretGenerator secrets;
    private final EmailSender emails;
    private final AuditTrail audit;
    private final TransactionRunner transactions;
    private final Clock clock;

    public RequestPasswordResetUseCase(CredentialRepository credentials, PasswordResetCodeRepository codes,
                                       PasswordHasher hasher, SecretGenerator secrets, EmailSender emails,
                                       AuditTrail audit, TransactionRunner transactions, Clock clock) {
        this.credentials = credentials;
        this.codes = codes;
        this.hasher = hasher;
        this.secrets = secrets;
        this.emails = emails;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
    }

    public void request(String rawIdentifier) {
        Optional<EmailTarget> resolved = PasswordResetLookup.resolveEmailTarget(credentials, rawIdentifier);
        Instant now = clock.instant();
        if (resolved.isEmpty()) {
            return;
        }
        long userId = resolved.get().credential().userId();
        EmailAddress email = resolved.get().email();
        if (codes.findLatest(userId).filter(code -> code.issuedRecently(now)).isPresent()) {
            return;                                           // chống spam: mã cũ vẫn dùng được
        }

        String rawCode = secrets.oneTimeCode();
        PasswordResetCode code = PasswordResetCode.issue(userId, rawCode, hasher, now);   // mã cũ hết ý nghĩa
        transactions.inTransaction(() -> {
            codes.save(code);
            audit.record(new AuditEntry(userId, AuditAction.YEU_CAU_KHOI_PHUC_MAT_KHAU, userId,
                    "Gửi mã khôi phục tới " + email.masked(), now));
            return null;
        });
        emails.send(new EmailMessage(email, "Mã khôi phục mật khẩu — Bãi gửi xe PTIT",
                "Xin chào " + resolved.get().credential().fullName() + ",\n\n"
                        + "Mã khôi phục mật khẩu của bạn là: " + rawCode + "\n"
                        + "Mã có hiệu lực " + PasswordResetCode.TTL.toMinutes() + " phút và chỉ dùng được một lần.\n"
                        + "Nếu bạn không yêu cầu, hãy bỏ qua email này."));
    }
}
