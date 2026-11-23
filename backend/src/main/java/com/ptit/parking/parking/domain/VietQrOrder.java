package com.ptit.parking.parking.domain;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Đơn thanh toán gửi xe bằng mã VietQR (Napas247) khi sinh viên không đủ điểm trong ví.
 */
public record VietQrOrder(
        long sessionId,
        String plateNumber,
        String studentCode,
        String fullName,
        long fee,
        String bankId,
        String accountNo,
        String accountName,
        String transferContent,
        String qrCodeUrl
) {

    /**
     * Sinh đơn thanh toán VietQR động theo chuẩn Napas247 (chuyển nhanh 24/7 bằng app ngân hàng bất kỳ).
     */
    public static VietQrOrder create(long sessionId, String plateNumber, String studentCode,
                                     String fullName, long fee, String bankId,
                                     String accountNo, String accountName, String template) {
        String transferContent = "TTGX " + sessionId;
        String encodedContent = URLEncoder.encode(transferContent, StandardCharsets.UTF_8).replace("+", "%20");
        String encodedName = URLEncoder.encode(accountName, StandardCharsets.UTF_8).replace("+", "%20");
        String qrCodeUrl = String.format(
                "https://img.vietqr.io/image/%s-%s-%s.png?amount=%d&addInfo=%s&accountName=%s",
                bankId, accountNo, template, fee, encodedContent, encodedName);

        return new VietQrOrder(
                sessionId, plateNumber, studentCode, fullName, fee,
                bankId, accountNo, accountName, transferContent, qrCodeUrl);
    }
}

