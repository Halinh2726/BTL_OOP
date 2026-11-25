package com.ptit.parking.notification.infrastructure.persistence;

import com.ptit.parking.notification.application.port.out.NotificationRepository;
import com.ptit.parking.shared.application.event.NotifiableEvent.Notice;
import com.ptit.parking.shared.application.event.NotifiableEvent.Recipient;
import com.ptit.parking.shared.application.event.NotifiableEvent.Role;
import com.ptit.parking.shared.application.event.NotifiableEvent.User;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Cài đặt {@link NotificationRepository} trên bảng notifications. */
@Component
class NotificationJdbcAdapter implements NotificationRepository {

    private final JdbcClient jdbc;

    NotificationJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void deliver(List<Notice> notices, Instant at) {
        LocalDateTime now = LocalDateTime.ofInstant(at, ZoneOffset.UTC);
        for (Notice notice : notices) {
            // switch trên sealed interface: thêm loại người nhận mới mà quên xử lý thì trình biên dịch báo lỗi
            switch (notice.to()) {
                case User user -> jdbc.sql("""
                                INSERT INTO notifications (user_id, kind, title, body, created_at)
                                VALUES (:user, :kind, :title, :body, :at)""")
                        .param("user", user.userId()).param("kind", notice.kind()).param("title", notice.title())
                        .param("body", notice.body()).param("at", now).update();
                case Role role -> jdbc.sql("""
                                INSERT INTO notifications (user_id, kind, title, body, created_at)
                                SELECT id, :kind, :title, :body, :at FROM users
                                WHERE role_code = :role AND status = 'HOAT_DONG'""")
                        .param("role", role.roleCode()).param("kind", notice.kind()).param("title", notice.title())
                        .param("body", notice.body()).param("at", now).update();
            }
        }
    }

    @Override
    public List<Stored> latest(long userId, int limit) {
        return jdbc.sql("""
                SELECT id, kind, title, body, created_at, read_at FROM notifications
                WHERE user_id = :user ORDER BY id DESC LIMIT :limit""")
                .param("user", userId).param("limit", limit)
                .query((rs, row) -> {
                    LocalDateTime read = rs.getObject("read_at", LocalDateTime.class);
                    return new Stored(rs.getLong("id"), rs.getString("kind"), rs.getString("title"), rs.getString("body"),
                            rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                            read == null ? null : read.toInstant(ZoneOffset.UTC));
                })
                .list();
    }

    @Override
    public long unreadCount(long userId) {
        return jdbc.sql("SELECT COUNT(*) FROM notifications WHERE user_id = :user AND read_at IS NULL")
                .param("user", userId).query(Long.class).single();
    }

    @Override
    public void markRead(long userId, long notificationId, Instant at) {
        jdbc.sql("UPDATE notifications SET read_at = :at WHERE id = :id AND user_id = :user AND read_at IS NULL")
                .param("at", LocalDateTime.ofInstant(at, ZoneOffset.UTC)).param("id", notificationId)
                .param("user", userId).update();
    }

    @Override
    public void markAllRead(long userId, Instant at) {
        jdbc.sql("UPDATE notifications SET read_at = :at WHERE user_id = :user AND read_at IS NULL")
                .param("at", LocalDateTime.ofInstant(at, ZoneOffset.UTC)).param("user", userId).update();
    }

    @Override
    public List<String> recipientEmails(Recipient recipient) {
        return switch (recipient) {
            case User user -> jdbc.sql("""
                            SELECT email FROM users
                            WHERE id = :user AND status = 'HOAT_DONG' AND email IS NOT NULL AND TRIM(email) <> ''""")
                    .param("user", user.userId())
                    .query(String.class)
                    .list();
            case Role role -> jdbc.sql("""
                            SELECT email FROM users
                            WHERE role_code = :role AND status = 'HOAT_DONG' AND email IS NOT NULL AND TRIM(email) <> ''""")
                    .param("role", role.roleCode())
                    .query(String.class)
                    .list();
        };
    }
}
