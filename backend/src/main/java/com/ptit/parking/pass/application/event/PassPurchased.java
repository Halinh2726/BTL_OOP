package com.ptit.parking.pass.application.event;

import com.ptit.parking.shared.application.event.NotifiableEvent;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Đợt 5 — sinh viên mua vé tháng thành công: gửi biên nhận vào hộp thông báo. */
public record PassPurchased(long studentId, String plateDisplay, YearMonth month, long price, LocalDate validUntil)
        implements NotifiableEvent {

    @Override
    public List<Notice> notices() {
        NumberFormat number = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"));
        return List.of(Notice.toUser(studentId, "VE_THANG",
                "Đã mua vé tháng " + month.format(DateTimeFormatter.ofPattern("MM/yyyy")),
                "Xe " + plateDisplay + " gửi miễn phí đến hết ngày "
                        + validUntil.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ". Đã trừ "
                        + number.format(price) + " điểm."));
    }
}
