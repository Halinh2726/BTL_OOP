package com.ptit.parking.student.adapter.out.persistence;

import com.ptit.parking.student.application.port.out.ParkingPresence;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Cài đặt {@link ParkingPresence}: open_vehicle_id = id xe khi lượt đang gửi (cột tính toán của V005). */
@Component
class ParkingPresenceJdbcAdapter implements ParkingPresence {

    private final JdbcClient jdbc;

    ParkingPresenceJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isParked(long vehicleId) {
        return jdbc.sql("SELECT COUNT(*) FROM parking_sessions WHERE open_vehicle_id = :id")
                .param("id", vehicleId)
                .query(Long.class)
                .single() > 0;
    }
}
