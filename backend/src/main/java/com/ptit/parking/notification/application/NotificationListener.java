package com.ptit.parking.notification.application;

import com.ptit.parking.notification.application.port.out.NotificationRepository;
import com.ptit.parking.shared.application.event.NotifiableEvent;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * "Người quan sát" (Observer) của mọi {@link NotifiableEvent}: chạy SAU KHI transaction của nghiệp vụ commit
 * (AFTER_COMMIT) — nghiệp vụ bị rollback thì không có thông báo. Sự kiện phát ngoài transaction vẫn được nhận
 * ({@code fallbackExecution}).
 * <p>Ghi thông báo lỗi thì chỉ ghi log: không bao giờ làm hỏng nghiệp vụ đã thành công (thông báo là phụ).
 */
@Component
class NotificationListener {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationListener.class);

    private final NotificationRepository notifications;
    private final Clock clock;

    NotificationListener(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @TransactionalEventListener(fallbackExecution = true)
    void on(NotifiableEvent event) {
        try {
            notifications.deliver(event.notices(), clock.instant());
        } catch (RuntimeException e) {
            LOG.warn("Không ghi được thông báo cho sự kiện {}", event.getClass().getSimpleName(), e);
        }
    }
}
