package com.ptit.parking.payment.domain;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Cấu hình tài khoản ngân hàng nhận tiền qua VietQR Napas247 (Value Object).
 * Đảm bảo mọi thông tin ngân hàng lưu hoặc sinh mã QR đều đã được chuẩn hóa và hợp lệ.
 */
public record BankAccountConfig(
        String bankId,
        String accountNo,
        String accountName,
        String template
) {

    public static final String DEFAULT_TEMPLATE = "compact2";
    public static final Set<String> ALLOWED_TEMPLATES = Set.of("compact2", "compact", "qr_only", "print");
    private static final Pattern BANK_ID_PATTERN = Pattern.compile("^[A-Z0-9]{2,20}$");
    private static final Pattern ACCOUNT_NO_PATTERN = Pattern.compile("^[A-Z0-9]{4,30}$");
    private static final Pattern ACCOUNT_NO_SEPARATORS = Pattern.compile("[\\s\\-.]");

    public BankAccountConfig {
        bankId = normalizeBankId(bankId);
        accountNo = normalizeAccountNo(accountNo);
        accountName = normalizeAccountName(accountName);
        template = normalizeTemplate(template);
    }

    public static String normalizeBankId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Mã ngân hàng không được để trống");
        }
        String normalized = raw.strip().toUpperCase(Locale.ROOT);
        if (!BANK_ID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Mã ngân hàng không hợp lệ (2-20 ký tự chữ hoa hoặc số): " + raw);
        }
        return normalized;
    }

    public static String normalizeAccountNo(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Số tài khoản không được để trống");
        }
        String normalized = ACCOUNT_NO_SEPARATORS.matcher(raw.strip()).replaceAll("").toUpperCase(Locale.ROOT);
        if (!ACCOUNT_NO_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Số tài khoản không hợp lệ (4-30 ký tự chữ/số): " + raw);
        }
        return normalized;
    }

    public static String normalizeAccountName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Tên chủ tài khoản không được để trống");
        }
        String normalized = raw.strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("Tên chủ tài khoản tối đa 100 ký tự");
        }
        return normalized;
    }

    public static String normalizeTemplate(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_TEMPLATE;
        }
        String normalized = raw.strip().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TEMPLATES.contains(normalized)) {
            throw new IllegalArgumentException("Mẫu ảnh VietQR không hợp lệ: " + raw);
        }
        return normalized;
    }

    /** Tạo URL ảnh VietQR chuẩn Napas247 kèm số tiền và nội dung chuyển khoản. */
    public String buildVietQrUrl(long amount, String transferContent) {
        String encodedContent = URLEncoder.encode(transferContent, StandardCharsets.UTF_8).replace("+", "%20");
        String encodedName = URLEncoder.encode(accountName, StandardCharsets.UTF_8).replace("+", "%20");
        return String.format(
                "https://img.vietqr.io/image/%s-%s-%s.png?amount=%d&addInfo=%s&accountName=%s",
                bankId, accountNo, template, amount, encodedContent, encodedName);
    }
}
