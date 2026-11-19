package com.ptit.parking.gate.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Ba ca trực trong ngày (khớp ck_shift_assignments_slot). Mỗi ca tự biết giờ bắt đầu / kết thúc — ca đêm kết thúc
 * vào sáng HÔM SAU, nơi dùng không phải tự xử lý trường hợp này.
 */
public enum ShiftSlot {
    SANG(6, 14),
    CHIEU(14, 22),
    DEM(22, 6);

    private final int startHour;
    private final int endHour;

    ShiftSlot(int startHour, int endHour) {
        this.startHour = startHour;
        this.endHour = endHour;
    }

    public int startHour() {
        return startHour;
    }

    public int endHour() {
        return endHour;
    }

    public Instant startsAt(LocalDate workDate, ZoneId zone) {
        return workDate.atTime(startHour, 0).atZone(zone).toInstant();
    }

    public Instant endsAt(LocalDate workDate, ZoneId zone) {
        LocalDate endDate = endHour > startHour ? workDate : workDate.plusDays(1);
        return endDate.atTime(endHour, 0).atZone(zone).toInstant();
    }
}
