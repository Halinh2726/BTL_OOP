package com.ptit.parking.parking.application.event;

import com.ptit.parking.shared.application.event.NotifiableEvent;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Sự kiện xe sinh viên vừa ra bãi — thông báo phí đã trừ (hoặc miễn phí vé tháng) và số dư ví còn lại. */
public record VehicleCheckedOut(
        long studentId,
        String plateDisplay,
        long fee,
        long balanceAfter,
        LocalDate passValidUntil,
        Instant exitedAt,
        ZoneId zone) implements NotifiableEvent {

    private static final DateTimeFormatter VN_TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    private static final DateTimeFormatter VN_DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    public List<Notice> notices() {
        NumberFormat number = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"));
        String at = ZonedDateTime.ofInstant(exitedAt, zone).format(VN_TIME);
        if (passValidUntil != null) {
            return List.of(Notice.toUser(
                    studentId,
                    "XE_RA",
                    "Xe " + plateDisplay + " vừa ra bãi (vé tháng)",
                    "Ra bãi lúc " + at + " · Miễn phí (vé tháng đến " + passValidUntil.format(VN_DAY)
                            + "). Số dư ví: " + number.format(balanceAfter) + " điểm."));
        }
        return List.of(Notice.toUser(
                studentId,
                "XE_RA",
                "Xe " + plateDisplay + " ra bãi — trừ " + number.format(fee) + " điểm",
                "Ra bãi lúc " + at + " · Đã trừ " + number.format(fee)
                        + " điểm. Số dư còn lại: " + number.format(balanceAfter) + " điểm."));
    }
}
