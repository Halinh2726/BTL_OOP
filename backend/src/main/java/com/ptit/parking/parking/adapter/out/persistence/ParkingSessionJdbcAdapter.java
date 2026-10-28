package com.ptit.parking.parking.adapter.out.persistence;

import com.ptit.parking.parking.application.port.out.ParkingSessionRepository;
import com.ptit.parking.parking.domain.ParkingSession;
import com.ptit.parking.parking.domain.SessionStatus;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

/** Cài đặt {@link ParkingSessionRepository} bằng SQL thuần. Thời điểm lưu theo UTC (quy ước chung của CSDL). */
@Component
class ParkingSessionJdbcAdapter implements ParkingSessionRepository {

    /** R05: mỗi xe nhiều nhất MỘT lượt DANG_GUI — UNIQUE trên cột tính toán open_vehicle_id (V005). */
    private static final String OPEN_SESSION_CONSTRAINT = "uq_parking_sessions_open";

    private static final String SELECT = """
            SELECT id, vehicle_id, status, entered_at, entry_guard_id, exited_at, exit_guard_id
            FROM parking_sessions
            """;

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
    public Optional<ParkingSession> findOpenByVehicle(long vehicleId) {
        return jdbc.sql(SELECT + "WHERE open_vehicle_id = :vehicleId")
                .param("vehicleId", vehicleId)
                .query(ParkingSessionJdbcAdapter::map)
                .optional();
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

    @Override
    public Optional<ParkingSession> lockById(long sessionId) {
        return jdbc.sql(SELECT + "WHERE id = :id FOR UPDATE")
                .param("id", sessionId)
                .query(ParkingSessionJdbcAdapter::map)
                .optional();
    }

    /** Chỉ cập nhật lượt CÒN đang gửi: lượt đã ra thì không ghi đè giờ ra / bảo vệ cho ra lần hai. */
    @Override
    public void saveExit(ParkingSession session) {
        int updated = jdbc.sql("""
                UPDATE parking_sessions SET status = :status, exited_at = :exitedAt, exit_guard_id = :exitGuardId
                WHERE id = :id AND status = 'DANG_GUI'""")
                .param("status", session.status().name())
                .param("exitedAt", LocalDateTime.ofInstant(session.exitedAt(), ZoneOffset.UTC))
                .param("exitGuardId", session.exitGuardId())
                .param("id", session.id())
                .update();
        if (updated != 1) {
            throw new DomainException(ErrorCode.SESSION_ALREADY_PAID);
        }
    }

    private static ParkingSession map(ResultSet rs, int row) throws SQLException {
        LocalDateTime exitedAt = rs.getObject("exited_at", LocalDateTime.class);
        return ParkingSession.restore(rs.getLong("id"), rs.getLong("vehicle_id"),
                SessionStatus.valueOf(rs.getString("status")),
                rs.getObject("entered_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                rs.getLong("entry_guard_id"),
                exitedAt == null ? null : exitedAt.toInstant(ZoneOffset.UTC),
                rs.getObject("exit_guard_id", Long.class));
    }
}
