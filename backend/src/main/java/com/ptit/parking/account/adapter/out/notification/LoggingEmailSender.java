package com.ptit.parking.account.adapter.out.notification;

import com.ptit.parking.account.application.port.out.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapter gửi email cho môi trường DEMO: in nội dung email ra log máy chủ thay vì gửi thật.
 * Khi chạy demo, mở cửa sổ backend sẽ thấy dòng {@code [EMAIL] ... Mã khôi phục mật khẩu của bạn là: 123456}.
 * <p>Triển khai thật: {@link SmtpEmailSender} được {@link EmailSenderConfig} chọn tự động khi có {@code spring.mail.host}.
 */
class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(EmailMessage message) {
        log.info("[EMAIL] Tới: {} | Tiêu đề: {}\n{}", message.to(), message.subject(), message.body());
    }
}
