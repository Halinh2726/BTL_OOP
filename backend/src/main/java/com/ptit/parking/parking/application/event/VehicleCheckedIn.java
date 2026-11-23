package com.ptit.parking.parking.application.event;

import com.ptit.parking.shared.application.event.NotifiableEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Sự kiện xe sinh viên vừa vào bãi — gửi thông báo vào hộp thư của chủ xe. */
public record VehicleCheckedIn(long studentId, String plateDisplay, Instant enteredAt, ZoneId zone)
        implements NotifiableEvent {

    private static final DateTimeFormatter VN_TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    @Override
    public List<Notice> notices() {
        String at = ZonedDateTime.ofInstant(enteredAt, zone).format(VN_TIME);
        return List.of(Notice.toUser(
                studentId,
                "XE_VAO",
                "Xe " + plateDisplay + " vừa vào bãi",
                "Hệ thống ghi nhận xe " + plateDisplay + " vào bãi lúc " + at + "."));
    }
}
