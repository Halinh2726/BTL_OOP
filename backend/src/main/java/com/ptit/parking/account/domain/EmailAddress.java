package com.ptit.parking.account.domain;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Địa chỉ email (value object) — dùng để nhận mã khôi phục mật khẩu. Luôn lưu chữ thường, bỏ khoảng trắng
 * hai đầu: "An@PTIT.edu.vn " và "an@ptit.edu.vn" là MỘT email (khớp ràng buộc uq_users_email).
 */
public record EmailAddress(String value) {

    public static final int MAX_LENGTH = 100;
    /** Đủ chặt để bắt lỗi gõ nhầm, khớp ck_users_email của CSDL. */
    public static final String PATTERN = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

    private static final Pattern VALID = Pattern.compile(PATTERN);

    public EmailAddress {
        if (value == null || value.length() > MAX_LENGTH || !VALID.matcher(value).matches()) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR);
        }
    }

    public static EmailAddress of(String raw) {
        return new EmailAddress(raw == null ? null : raw.strip().toLowerCase(Locale.ROOT));
    }

    /** Ô email để trống nghĩa là "không có email" — không phải lỗi. */
    public static Optional<EmailAddress> ofNullable(String raw) {
        return raw == null || raw.isBlank() ? Optional.empty() : Optional.of(of(raw));
    }

    /** Che bớt khi ghi nhật ký: "an@ptit.edu.vn" → "a***@ptit.edu.vn". */
    public String masked() {
        int at = value.indexOf('@');
        return value.charAt(0) + "***" + value.substring(at);
    }

    @Override
    public String toString() {
        return value;
    }
}
