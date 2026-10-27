package com.ptit.parking.payment.application;

import com.ptit.parking.payment.domain.TransactionType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.stream.Collectors;

/**
 * "Dấu vân tay" SHA-256 của nội dung một request thay đổi điểm (Đặc tả API mục 6): loại giao dịch,
 * người thực hiện và các tham số trong body. Cùng Idempotency-Key mà khác dấu vân tay
 * → IDEMPOTENCY_KEY_REUSED.
 */
public final class RequestFingerprint {

    private RequestFingerprint() {
    }

    public static String of(TransactionType type, long performedBy, Object... parameters) {
        String canonical = type + "|" + performedBy + "|"
                + Arrays.stream(parameters).map(String::valueOf).collect(Collectors.joining("|"));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM thiếu SHA-256", e);    // không xảy ra: mọi JVM đều có
        }
    }
}
