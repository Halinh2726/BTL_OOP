package com.ptit.parking.student.domain;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Biển số xe đã chuẩn hóa (value object). "29a-123.45" và "29A 12345" là CÙNG một xe: mọi nơi nhận
 * biển số (sinh viên đăng ký, bảo vệ tra cứu khi xe vào/ra) đều đi qua lớp này nên so khớp luôn đúng.
 * <p>Đối tượng tạo ra được thì CHẮC CHẮN hợp lệ — không có biển số "nửa đúng nửa sai" trong hệ thống.
 * <p>Đợt 1 — biển số theo quy định:
 * <ul>
 *   <li>Đăng ký xe MỚI phải đúng dạng biển Việt Nam {@link #STANDARD_PATTERN}: 2 số tỉnh + 1–2 chữ seri
 *       (+ 1 số seri với xe máy) + 4–5 số, ví dụ 29A-1514, 29A-123.45, 29A1-123.45.</li>
 *   <li>Xe đạp không có biển dùng mã do bãi cấp XD-xxxxx ({@link #bicycleCode}).</li>
 *   <li>Vẫn LƯU dạng chuẩn hóa (chỉ chữ và số) để tìm kiếm và chống trùng; {@link #display()} thêm dấu khi hiển thị.</li>
 * </ul>
 */
public record PlateNumber(String value) {

    /** Định dạng SAU chuẩn hóa dùng khi tra cứu (Đặc tả API #6) — khớp ràng buộc ck_vehicles_plate của CSDL. */
    public static final String PATTERN = "^[A-Z0-9]{5,20}$";

    /** Biển số Việt Nam hoặc mã xe đạp, SAU chuẩn hóa: bắt buộc khi đăng ký xe mới. */
    public static final String STANDARD_PATTERN = "^([0-9]{2}[A-Z]{1,2}[0-9]{4,6}|XD[0-9]{5})$";

    public static final String BICYCLE_PREFIX = "XD";

    private static final Pattern VALID = Pattern.compile(PATTERN);
    private static final Pattern STANDARD = Pattern.compile(STANDARD_PATTERN);
    private static final Pattern SEPARATORS = Pattern.compile("[\\s.\\-]");
    /** Tách phần seri và phần số: 2 số tỉnh + chữ seri, rồi phần còn lại toàn số. */
    private static final Pattern PARTS = Pattern.compile("^([0-9]{2}[A-Z]{1,2})([0-9]{4,6})$");
    private static final Pattern BICYCLE = Pattern.compile("^XD([0-9]{5})$");

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

    /** Mã bãi cấp cho xe đạp không biển: số thứ tự 1 → "XD00001". */
    public static PlateNumber bicycleCode(int sequence) {
        if (sequence < 1 || sequence > 99_999) {
            throw new IllegalArgumentException("Số thứ tự mã xe đạp phải từ 1 đến 99999: " + sequence);
        }
        return new PlateNumber(BICYCLE_PREFIX + String.format("%05d", sequence));
    }

    /** Đúng quy định cho xe đăng ký mới (biển Việt Nam hoặc mã XD). */
    public boolean isStandard() {
        return STANDARD.matcher(value).matches();
    }

    public boolean isBicycleCode() {
        return BICYCLE.matcher(value).matches();
    }

    /**
     * Dạng hiển thị: "XD00001" → "XD-00001"; 4 số cuối → "29A-1514"; 5 số → "29A-123.45";
     * 6 số (xe máy có số seri) → "29A1-123.45". Dữ liệu cũ không đúng quy định thì hiển thị nguyên văn.
     */
    public String display() {
        Matcher bicycle = BICYCLE.matcher(value);
        if (bicycle.matches()) {
            return BICYCLE_PREFIX + "-" + bicycle.group(1);
        }
        Matcher parts = PARTS.matcher(value);
        if (!parts.matches()) {
            return value;
        }
        String series = parts.group(1);
        String digits = parts.group(2);
        if (digits.length() == 6) {                 // 1 số seri + 5 số: 29A1 + 123.45
            series += digits.charAt(0);
            digits = digits.substring(1);
        }
        return digits.length() == 5
                ? series + "-" + digits.substring(0, 3) + "." + digits.substring(3)
                : series + "-" + digits;
    }

    @Override
    public String toString() {
        return value;
    }
}
