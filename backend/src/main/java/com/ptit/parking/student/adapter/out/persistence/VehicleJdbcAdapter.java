package com.ptit.parking.student.adapter.out.persistence;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import com.ptit.parking.student.application.port.out.VehicleRepository;
import com.ptit.parking.student.domain.PlateNumber;
import com.ptit.parking.student.domain.Vehicle;

/** Cài đặt {@link VehicleRepository} bằng SQL thuần. Thời điểm lưu theo UTC (quy ước chung của CSDL). */
@Component
class VehicleJdbcAdapter implements VehicleRepository {

    private final JdbcClient jdbc;

    VehicleJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean plateExists(PlateNumber plate) {
        return jdbc.sql("SELECT COUNT(*) FROM vehicles WHERE plate_number = :plate")
                .param("plate", plate.value())
                .query(Long.class)
                .single() > 0;
    }

    @Override
    public Vehicle save(Vehicle vehicle) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO vehicles (student_id, plate_number, vehicle_type_code, registered_at)
                VALUES (:studentId, :plate, :type, :registeredAt)""")
                .param("studentId", vehicle.studentId())
                .param("plate", vehicle.plate().value())
                .param("type", vehicle.vehicleTypeCode())
                .param("registeredAt", LocalDateTime.ofInstant(vehicle.registeredAt(), ZoneOffset.UTC))
                .update(keys);
        return vehicle.withId(keys.getKey().longValue());
    }
}