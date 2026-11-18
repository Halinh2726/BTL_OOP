package com.ptit.parking.account.domain;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;

/**
 * Quy tắc mật khẩu mới — MỘT nơi duy nhất, dùng chung cho đổi mật khẩu, quên mật khẩu và đổi mật khẩu tạm.
 * 8–72 ký tự: tối thiểu 8 cho đủ khó đoán; tối đa 72 vì BCrypt chỉ dùng 72 byte đầu (dài hơn là cảm giác an toàn giả).
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 72;

    private PasswordPolicy() {
    }

    public static void requireAcceptable(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_LENGTH || rawPassword.length() > MAX_LENGTH
                || rawPassword.isBlank()) {
            throw new DomainException(ErrorCode.PASSWORD_POLICY);
        }
    }
}
