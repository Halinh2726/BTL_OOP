package com.ptit.parking.account.application.event;

import com.ptit.parking.shared.application.event.NotifiableEvent;
import java.util.List;

/** Thông báo bảo mật gửi vào hộp thư của chủ tài khoản khi mật khẩu thay đổi / khôi phục / cấp tạm. */
public record SecurityChanged(long userId, String title, String body) implements NotifiableEvent {

    @Override
    public List<Notice> notices() {
        return List.of(Notice.toUser(userId, "BAO_MAT", title, body));
    }
}
