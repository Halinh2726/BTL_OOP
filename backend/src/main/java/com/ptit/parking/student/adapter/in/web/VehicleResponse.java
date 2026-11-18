package com.ptit.parking.student.adapter.in.web;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import com.ptit.parking.student.domain.Vehicle;

/**
 * Một xe trong response của API #5 và #6. registeredAt hiển thị theo giờ Việt Nam kèm múi giờ,
 * ví dụ "2026-10-01T08:00:00+07:00" (CSDL lưu UTC).
 * <p>Đợt 1: plateDisplay là biển số có dấu theo quy định ("29A-123.45"); plateNumber vẫn là dạng chuẩn hóa
 * (dùng cho mã QR, tra cứu). status DANG_DUNG / NGUNG_SU_DUNG.
 */
record VehicleResponse(long id, String plateNumber, String plateDisplay, String vehicleTypeCode, String status,
                       String registeredAt, String deactivatedAt) {

    static VehicleResponse from(Vehicle vehicle, ZoneId zone) {
        return new VehicleResponse(vehicle.id(), vehicle.plate().value(), vehicle.plate().display(),
                vehicle.vehicleTypeCode(), vehicle.status().name(),
                format(vehicle.registeredAt(), zone), format(vehicle.deactivatedAt(), zone));
    }

    private static String format(Instant instant, ZoneId zone) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, zone)
                .truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
