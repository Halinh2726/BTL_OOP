package com.ptit.parking.account.domain;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.util.Optional;
import java.util.regex.Pattern;

/** Số điện thoại Việt Nam 10–11 chữ số bắt đầu bằng 0 (value object) — khớp ck_guards_phone, ck_students_phone. */
public record PhoneNumber(String value) {

    public static final String PATTERN = "^0[0-9]{9,10}$";

    private static final Pattern VALID = Pattern.compile(PATTERN);

    public PhoneNumber {
        if (value == null || !VALID.matcher(value).matches()) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR);
        }
    }

    /** Bỏ khoảng trắng, dấu chấm, gạch ngang người dùng hay gõ: "091 234.5678" → "0912345678". */
    public static PhoneNumber of(String raw) {
        return new PhoneNumber(raw == null ? null : raw.replaceAll("[\\s.\\-]", ""));
    }

    public static Optional<PhoneNumber> ofNullable(String raw) {
        return raw == null || raw.isBlank() ? Optional.empty() : Optional.of(of(raw));
    }

    @Override
    public String toString() {
        return value;
    }
}
