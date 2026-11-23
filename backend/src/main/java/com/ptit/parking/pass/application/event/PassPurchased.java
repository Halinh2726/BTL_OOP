package com.ptit.parking.pass.application.event;

import com.ptit.parking.shared.application.event.NotifiableEvent;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Đợt 5 — sinh viên mua vé tháng (1 hoặc nhiều tháng liên tiếp): gửi biên nhận vào hộp thông báo. */
public record PassPurchased(
        long studentId,
        String plateDisplay,
        YearMonth month,
        long price,
        LocalDate validUntil,
        int monthsCount) implements NotifiableEvent {

    private static final DateTimeFormatter MM_YYYY = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public PassPurchased(long studentId, String plateDisplay, YearMonth month, long price, LocalDate validUntil) {
        this(studentId, plateDisplay, month, price, validUntil, 1);
    }

    @Override
    public List<Notice> notices() {
        NumberFormat number = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"));
        String title;
        if (monthsCount <= 1) {
            title = "Đã mua vé tháng " + month.format(MM_YYYY);
        } else {
            YearMonth endMonth = month.plusMonths(monthsCount - 1L);
            title = "Đã mua vé " + monthsCount + " tháng (" + month.format(MM_YYYY) + "–" + endMonth.format(MM_YYYY) + ")";
        }
        return List.of(Notice.toUser(studentId, "VE_THANG", title,
                "Xe " + plateDisplay + " gửi miễn phí đến hết ngày "
                        + validUntil.format(DD_MM_YYYY) + ". Đã trừ "
                        + number.format(price) + " điểm."));
    }
}
