package com.ptit.parking.notification.application.port.out;

import com.ptit.parking.shared.application.event.NotifiableEvent.Notice;
import com.ptit.parking.shared.application.event.NotifiableEvent.Recipient;
import java.time.Instant;
import java.util.List;

/** Port: hộp thông báo của người dùng (bảng notifications). */
public interface NotificationRepository {

    /** Một thông báo đã lưu; {@code readAt} null = chưa đọc. */
    record Stored(long id, String kind, String title, String body, Instant createdAt, Instant readAt) {}

    /** Ghi các thông báo; người nhận theo vai trò được mở rộng thành từng người dùng ĐANG HOẠT ĐỘNG của vai trò đó. */
    void deliver(List<Notice> notices, Instant at);

    List<Stored> latest(long userId, int limit);

    long unreadCount(long userId);

    /** Chỉ đánh dấu thông báo CỦA người này — id của người khác thì không làm gì. */
    void markRead(long userId, long notificationId, Instant at);

    void markAllRead(long userId, Instant at);

    /** Danh sách email (không null) của những người dùng đang hoạt động tương ứng với {@code recipient}. */
    default List<String> recipientEmails(Recipient recipient) {
        return List.of();
    }
}
