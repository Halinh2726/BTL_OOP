package com.ptit.parking.shared.application.event;

import java.util.List;
import java.util.Objects;

/**
 * Đợt 5 — sự kiện nghiệp vụ cần báo cho người dùng (mẫu <b>Observer</b>). Use case chỉ PHÁT sự kiện
 * ({@link com.ptit.parking.shared.application.port.out.EventPublisher}); bộ lắng nghe ở module notification ghi thông
 * báo. Use case không biết ai nghe, bộ nghe không biết use case nào phát — thêm kênh mới (email, Zalo...) chỉ cần thêm
 * một bộ nghe, không sửa use case.
 * <p>Đa hình: MỖI sự kiện tự biết báo cho ai, nội dung gì ({@link #notices()}) — bộ nghe không có if/else theo loại.
 */
public interface NotifiableEvent {

    List<Notice> notices();

    /** Người nhận: một người dùng cụ thể, hoặc mọi người của một vai trò (ví dụ mọi quản lý). */
    sealed interface Recipient permits User, Role {}

    record User(long userId) implements Recipient {}

    record Role(String roleCode) implements Recipient {}

    /** Một thông báo. {@code kind} để giao diện chọn biểu tượng / màu. */
    record Notice(Recipient to, String kind, String title, String body) {

        public static final int MAX_TITLE = 150;
        public static final int MAX_BODY = 500;

        public Notice {
            Objects.requireNonNull(to, "to");
            Objects.requireNonNull(kind, "kind");
            title = cut(title, MAX_TITLE);
            body = cut(body, MAX_BODY);
        }

        private static String cut(String text, int max) {
            String value = text == null ? "" : text.strip();
            return value.length() > max ? value.substring(0, max - 1) + "…" : value;
        }

        public static Notice toUser(long userId, String kind, String title, String body) {
            return new Notice(new User(userId), kind, title, body);
        }

        public static Notice toRole(String roleCode, String kind, String title, String body) {
            return new Notice(new Role(roleCode), kind, title, body);
        }
    }
}
