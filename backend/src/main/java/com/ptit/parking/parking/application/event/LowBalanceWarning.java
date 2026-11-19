package com.ptit.parking.parking.application.event;

import com.ptit.parking.shared.application.event.NotifiableEvent;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/** Đợt 5 — sau khi trả phí, số điểm còn lại không đủ cho lượt gửi tiếp theo: nhắc sinh viên đổi thêm điểm. */
public record LowBalanceWarning(long studentId, long balance, long minimumFee) implements NotifiableEvent {

    @Override
    public List<Notice> notices() {
        NumberFormat number = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"));
        return List.of(Notice.toUser(studentId, "SO_DU_THAP", "Số điểm sắp hết",
                "Bạn còn " + number.format(balance) + " điểm, chưa đủ " + number.format(minimumFee)
                        + " điểm cho lượt gửi tiếp theo. Hãy đổi thêm điểm tại quầy quản lý hoặc mua vé tháng."));
    }
}
