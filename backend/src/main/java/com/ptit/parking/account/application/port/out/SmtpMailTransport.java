package com.ptit.parking.account.application.port.out;

import com.ptit.parking.account.application.port.out.EmailSender.EmailMessage;

/** Port gửi email trực tiếp qua SMTP (dùng cho nút "Gửi email thử" để báo lỗi cấu hình ngay lập tức). */
@FunctionalInterface
public interface SmtpMailTransport {

    void sendDirect(EmailMessage message);
}
