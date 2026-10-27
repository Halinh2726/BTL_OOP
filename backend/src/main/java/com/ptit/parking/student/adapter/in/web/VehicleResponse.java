package com.ptit.parking.student.adapter.in.web;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import com.ptit.parking.student.domain.Vehicle;

/**
 * Một xe trong response của API #5 và #6. registeredAt hiển thị theo giờ Việt Nam kèm múi giờ,
 * ví dụ "2026-10-01T08:00:00+07:00" (CSDL lưu UTC).
 */
record VehicleResponse(long id, String plateNumber, String vehicleTypeCode, String registeredAt) {

    static VehicleResponse from(Vehicle vehicle, ZoneId zone) {
        String registeredAt = OffsetDateTime.ofInstant(vehicle.registeredAt(), zone)
                .truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return new VehicleResponse(vehicle.id(), vehicle.plate().value(), vehicle.vehicleTypeCode(), registeredAt);
    }
}