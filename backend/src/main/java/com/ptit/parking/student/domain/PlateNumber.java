package com.ptit.parking.student.domain;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Biển số xe đã chuẩn hóa (value object). "29a-123.45" và "29A 12345" là CÙNG một xe: mọi nơi nhận
 * biển số (sinh viên đăng ký, bảo vệ tra cứu khi xe vào/ra) đều đi qua lớp này nên so khớp luôn đúng.
 * <p>Đối tượng tạo ra được thì CHẮC CHẮN hợp lệ — không có biển số "nửa đúng nửa sai" trong hệ thống.
 */
public record PlateNumber(String value) {

    /** Định dạng SAU chuẩn hóa (Đặc tả API #6) — khớp ràng buộc ck_vehicles_plate của CSDL. */
    public static final String PATTERN = "^[A-Z0-9]{5,20}$";

    private static final Pattern VALID = Pattern.compile(PATTERN);
    private static final Pattern SEPARATORS = Pattern.compile("[\\s.\\-]");

    public PlateNumber {
        if (value == null || !VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Biển số không hợp lệ: " + value);
        }
    }

    /** Chuẩn hóa: viết hoa, bỏ khoảng trắng, dấu chấm và gạch ngang. Ví dụ "29a-123.45" → "29A12345". */
    public static String normalize(String raw) {
        return raw == null ? null : SEPARATORS.matcher(raw).replaceAll("").toUpperCase(Locale.ROOT);
    }

    public static PlateNumber of(String raw) {
        return new PlateNumber(normalize(raw));
    }
    /** Biển số người dùng gõ (bảo vệ tra cứu): sai định dạng thì rỗng thay vì ném lỗi. */
    public static Optional<PlateNumber> tryParse(String raw) {
        String normalized = normalize(raw);
        return normalized != null && VALID.matcher(normalized).matches()
                ? Optional.of(new PlateNumber(normalized))
                : Optional.empty();
    }
    @Override
    public String toString() {
        return value;
    }
}