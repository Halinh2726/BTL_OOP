package com.ptit.parking.account.application.port.out;

import com.ptit.parking.account.domain.EmailAddress;

/**
 * Port: gửi email. Use case không biết email đi bằng SMTP, dịch vụ gửi thư hay chỉ ghi ra log —
 * bản hiện tại dùng {@code LoggingEmailSender} (in nội dung ra log máy chủ, đủ cho demo); muốn gửi thật chỉ cần
 * thêm một adapter mới, use case không đổi (Dependency Inversion).
 */
public interface EmailSender {

    record EmailMessage(EmailAddress to, String subject, String body) {}

    void send(EmailMessage message);
}
