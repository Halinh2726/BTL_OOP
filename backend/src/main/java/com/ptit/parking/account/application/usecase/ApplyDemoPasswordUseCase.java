package com.ptit.parking.account.application.usecase;

import com.ptit.parking.account.application.port.out.CredentialRepository;
import com.ptit.parking.account.domain.PasswordPolicy;
import com.ptit.parking.auth.domain.PasswordHasher;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import java.time.Clock;

/**
 * CHỈ DÙNG KHI DEMO: đặt cùng một mật khẩu cố định cho MỌI tài khoản (quản lý, chủ bãi xe, bảo vệ, sinh viên),
 * để người chấm / người xem demo đăng nhập được mọi vai trò mà không cần nhớ từng mật khẩu.
 * <p>Mật khẩu demo vẫn phải đúng {@link PasswordPolicy} (8–72 ký tự). Băm BCrypt MỘT lần rồi ghi cho mọi tài khoản
 * (cùng một hash) — nhanh dù có hàng trăm tài khoản. Cờ "mật khẩu tạm" được bỏ để vào thẳng màn hình chính.
 */
public class ApplyDemoPasswordUseCase {

    private final CredentialRepository credentials;
    private final PasswordHasher hasher;
    private final TransactionRunner transactions;
    private final Clock clock;

    public ApplyDemoPasswordUseCase(CredentialRepository credentials, PasswordHasher hasher,
                                    TransactionRunner transactions, Clock clock) {
        this.credentials = credentials;
        this.hasher = hasher;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** @return số tài khoản đã đặt lại */
    public int apply(String demoPassword) {
        PasswordPolicy.requireAcceptable(demoPassword);
        String hash = hasher.hash(demoPassword);
        return transactions.inTransaction(() -> credentials.resetAllPasswords(hash, clock.instant()));
    }
}
