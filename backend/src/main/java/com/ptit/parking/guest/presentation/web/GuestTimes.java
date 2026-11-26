package com.ptit.parking.guest.presentation.web;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Giờ trả về cho giao diện là giờ Việt Nam kèm múi giờ ("2026-10-05T18:30:00+07:00"), như mọi API khác.
 * CSDL lưu UTC; không trả thẳng {@link Instant} vì giao diện hiển thị đúng giờ ghi trong chuỗi.
 */
final class GuestTimes {

    private GuestTimes() {
    }

    static String format(Instant instant, ZoneId zone) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, zone).truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
