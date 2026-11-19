package com.ptit.parking.notification.application;

import com.ptit.parking.notification.application.port.out.NotificationRepository;
import com.ptit.parking.notification.application.port.out.NotificationRepository.Stored;
import java.time.Clock;
import java.util.List;

/** Đợt 5 — "thông báo của tôi": mọi vai trò đều có; luôn là hộp thư của người đang đăng nhập. */
public class MyNotificationsUseCase {

    public static final int LIMIT = 30;

    public record Inbox(long unread, List<Stored> items) {}

    private final NotificationRepository notifications;
    private final Clock clock;

    public MyNotificationsUseCase(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    public Inbox inbox(long userId) {
        return new Inbox(notifications.unreadCount(userId), notifications.latest(userId, LIMIT));
    }

    public Inbox markRead(long userId, long notificationId) {
        notifications.markRead(userId, notificationId, clock.instant());
        return inbox(userId);
    }

    public Inbox markAllRead(long userId) {
        notifications.markAllRead(userId, clock.instant());
        return inbox(userId);
    }
}
