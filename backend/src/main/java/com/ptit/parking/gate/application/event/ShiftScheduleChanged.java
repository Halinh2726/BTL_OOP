package com.ptit.parking.gate.application.event;

import com.ptit.parking.gate.domain.ShiftSlot;
import com.ptit.parking.shared.application.event.NotifiableEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Đợt 5 — quản lý xếp / gỡ một ca của bảo vệ: báo bảo vệ đó. */
public record ShiftScheduleChanged(long guardId, LocalDate date, ShiftSlot slot, boolean assigned)
        implements NotifiableEvent {

    @Override
    public List<Notice> notices() {
        String when = "ca " + label(slot) + " ngày " + date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        return List.of(assigned
                ? Notice.toUser(guardId, "PHAN_CA", "Bạn được xếp " + when, "Xem tab Ca trực → Lịch trực của tôi.")
                : Notice.toUser(guardId, "PHAN_CA", "Quản lý đã gỡ " + when, "Bạn không phải trực ca này nữa."));
    }

    private static String label(ShiftSlot slot) {
        return switch (slot) {
            case SANG -> "sáng (06:00–14:00)";
            case CHIEU -> "chiều (14:00–22:00)";
            case DEM -> "đêm (22:00–06:00)";
        };
    }
}
