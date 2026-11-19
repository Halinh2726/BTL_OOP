package com.ptit.parking.notification.adapter.in.web;

import com.ptit.parking.notification.application.MyNotificationsUseCase;
import com.ptit.parking.notification.application.MyNotificationsUseCase.Inbox;
import com.ptit.parking.shared.security.SessionUser;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Đợt 5 — hộp thông báo của người đang đăng nhập (mọi vai trò; chỉ cần đăng nhập, không cần quyền riêng). */
@RestController
@RequestMapping("/api/v1/me/notifications")
class NotificationController {

    record NotificationItem(long id, String kind, String title, String body, String createdAt, boolean read) {}

    record InboxResponse(long unread, List<NotificationItem> items) {}

    private final MyNotificationsUseCase notifications;
    private final ZoneId zone;

    NotificationController(MyNotificationsUseCase notifications, Clock clock) {
        this.notifications = notifications;
        this.zone = clock.getZone();
    }

    @GetMapping
    InboxResponse inbox(@AuthenticationPrincipal SessionUser user) {
        return view(notifications.inbox(user.id()));
    }

    @PostMapping("/{id}/read")
    InboxResponse read(@AuthenticationPrincipal SessionUser user, @PathVariable long id) {
        return view(notifications.markRead(user.id(), id));
    }

    @PostMapping("/read-all")
    InboxResponse readAll(@AuthenticationPrincipal SessionUser user) {
        return view(notifications.markAllRead(user.id()));
    }

    private InboxResponse view(Inbox inbox) {
        return new InboxResponse(inbox.unread(), inbox.items().stream()
                .map(n -> new NotificationItem(n.id(), n.kind(), n.title(), n.body(), time(n.createdAt()), n.readAt() != null))
                .toList());
    }

    private String time(Instant instant) {
        return OffsetDateTime.ofInstant(instant, zone).truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
