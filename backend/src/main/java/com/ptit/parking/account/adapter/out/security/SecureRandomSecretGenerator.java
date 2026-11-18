package com.ptit.parking.account.adapter.out.security;

import com.ptit.parking.account.application.port.out.SecretGenerator;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Cài đặt {@link SecretGenerator} bằng {@link SecureRandom} — bộ sinh số ngẫu nhiên dùng được cho mục đích bảo mật. */
@Component
class SecureRandomSecretGenerator implements SecretGenerator {

    /** Bỏ 0, O, 1, l, I để người dùng không đọc nhầm khi quản lý đọc mật khẩu tạm. */
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final int TEMPORARY_LENGTH = 10;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String oneTimeCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    @Override
    public String temporaryPassword() {
        StringBuilder password = new StringBuilder(TEMPORARY_LENGTH);
        for (int i = 0; i < TEMPORARY_LENGTH; i++) {
            password.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return password.toString();
    }
}
