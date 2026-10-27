package com.ptit.parking.parking.adapter.out.persistence;

import com.ptit.parking.parking.application.port.out.ParkingSessionRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Cài đặt {@link ParkingSessionRepository} bằng SQL thuần. */
@Component
class ParkingSessionJdbcAdapter implements ParkingSessionRepository {

    private final JdbcClient jdbc;

    ParkingSessionJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Cột open_vehicle_id do CSDL tự tính (= vehicle_id khi DANG_GUI), có UNIQUE nên tra theo nó rất nhanh. */
    @Override
    public boolean hasOpenSession(long vehicleId) {
        return jdbc.sql("SELECT COUNT(*) FROM parking_sessions WHERE open_vehicle_id = :vehicleId")
                .param("vehicleId", vehicleId)
                .query(Long.class)
                .single() > 0;
    }
}