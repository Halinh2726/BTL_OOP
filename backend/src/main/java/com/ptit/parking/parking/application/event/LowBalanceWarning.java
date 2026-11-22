package com.ptit.parking.parking.application.event;

import com.ptit.parking.shared.application.event.NotifiableEvent;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Đợt 5 & mở rộng cảnh báo sớm: nhắc sinh viên khi ví không đủ lượt tiếp theo hoặc sắp hết điểm (< 2 lượt tối thiểu).
 */
public record LowBalanceWarning(long studentId, long balance, long minimumFee) implements NotifiableEvent {

    @Override
    public List<Notice> notices() {
        NumberFormat number = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"));
        if (balance < minimumFee) {
            return List.of(Notice.toUser(studentId, "SO_DU_THAP", "Số điểm không đủ lượt tiếp theo",
                    "Bạn còn " + number.format(balance) + " điểm, chưa đủ " + number.format(minimumFee)
                            + " điểm cho lượt gửi tiếp theo. Hãy nạp thêm điểm hoặc mua vé tháng."));
        }
        return List.of(Notice.toUser(studentId, "SO_DU_THAP", "Số điểm sắp hết",
                "Bạn còn " + number.format(balance) + " điểm (chỉ đủ khoảng 1 lượt gửi tối thiểu "
                        + number.format(minimumFee) + " điểm). Hãy nạp thêm điểm hoặc mua vé tháng để tránh gián đoạn."));
    }
}
