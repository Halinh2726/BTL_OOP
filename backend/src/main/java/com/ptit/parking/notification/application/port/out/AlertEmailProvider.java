package com.ptit.parking.notification.application.port.out;

import com.ptit.parking.account.domain.EmailAddress;
import java.util.Optional;

/** Port đọc email nhận cảnh báo sự cố & trạng thái SMTP hiện tại. */
public interface AlertEmailProvider {

    boolean isSmtpActive();

    Optional<EmailAddress> alertEmail();
}
