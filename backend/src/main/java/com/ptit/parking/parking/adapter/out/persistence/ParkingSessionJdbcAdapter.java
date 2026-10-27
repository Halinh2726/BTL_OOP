package com.ptit.parking.parking.adapter.out.persistence;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import com.ptit.parking.parking.application.port.out.ParkingSessionRepository;
import com.ptit.parking.parking.domain.ParkingSession;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;

/** Cài đặt {@link ParkingSessionRepository} bằng SQL thuần. Thời điểm lưu theo UTC (quy ước chung của CSDL). */
@Component
class ParkingSessionJdbcAdapter implements ParkingSessionRepository {

    /** R05: mỗi xe nhiều nhất MỘT lượt DANG_GUI — UNIQUE trên cột tính toán open_vehicle_id (V005). */
    private static final String OPEN_SESSION_CONSTRAINT = "uq_parking_sessions_open";

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

    @Override
    public ParkingSession save(ParkingSession session) {
        KeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.sql("""
                    INSERT INTO parking_sessions (vehicle_id, status, entered_at, entry_guard_id)
                    VALUES (:vehicleId, :status, :enteredAt, :entryGuardId)""")
                    .param("vehicleId", session.vehicleId())
                    .param("status", session.status().name())
                    .param("enteredAt", LocalDateTime.ofInstant(session.enteredAt(), ZoneOffset.UTC))
                    .param("entryGuardId", session.entryGuardId())
                    .update(keys);
        } catch (DuplicateKeyException e) {
            if (String.valueOf(e.getMessage()).contains(OPEN_SESSION_CONSTRAINT)) {
                throw new DomainException(ErrorCode.SESSION_ALREADY_OPEN);
            }
            throw e;
        }
        return session.withId(keys.getKey().longValue());
    }
}
