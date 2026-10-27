package com.ptit.parking.parking.application.port.out;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.ptit.parking.parking.domain.ParkingSession;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;

/**
 * Kho lượt gửi GIẢ trong bộ nhớ, dùng chung cho test các use case xe vào/xe ra.
 * <p>Giống CSDL thật ở hai chỗ quan trọng: mỗi xe nhiều nhất MỘT lượt đang mở (như uq_parking_sessions_open),
 * và đối tượng trả ra là BẢN SAO — đóng lượt mà quên lưu thì kho không đổi.
 * Các hàm {@code synchronized} để test nhiều luồng dùng được.
 */
public class InMemoryParkingSessionRepository implements ParkingSessionRepository {

    private final Map<Long, ParkingSession> byId = new LinkedHashMap<>();
    private long nextId = 1;

    @Override
    public synchronized boolean hasOpenSession(long vehicleId) {
        return findOpenByVehicle(vehicleId).isPresent();
    }

    @Override
    public synchronized Optional<ParkingSession> findOpenByVehicle(long vehicleId) {
        return byId.values().stream()
                .filter(s -> s.isOpen() && s.vehicleId() == vehicleId)
                .findFirst()
                .map(InMemoryParkingSessionRepository::copy);
    }

    @Override
    public synchronized ParkingSession save(ParkingSession session) {
        if (session.isOpen() && hasOpenSession(session.vehicleId())) {
            throw new DomainException(ErrorCode.SESSION_ALREADY_OPEN);      // như ràng buộc UNIQUE của CSDL
        }
        return store(session);
    }

    /** Chuẩn bị dữ liệu test: xe này đang có lượt gửi chưa ra. */
    public ParkingSession markOpen(long vehicleId) {
        return markOpen(vehicleId, Instant.parse("2026-10-27T00:55:00Z"));
    }

    /** Chuẩn bị dữ liệu test: xe này vào bãi lúc {@code enteredAt} và chưa ra. Trả về lượt đã có id. */
    public synchronized ParkingSession markOpen(long vehicleId, Instant enteredAt) {
        return store(ParkingSession.open(vehicleId, 99L, enteredAt));
    }

    /** Các lượt đã lưu (bản sao). */
    public synchronized List<ParkingSession> all() {
        return byId.values().stream().map(InMemoryParkingSessionRepository::copy).toList();
    }

    private ParkingSession store(ParkingSession session) {
        ParkingSession withId = session.withId(nextId++);
        byId.put(withId.id(), copy(withId));
        return withId;
    }

    private static ParkingSession copy(ParkingSession s) {
        return ParkingSession.restore(s.id(), s.vehicleId(), s.status(), s.enteredAt(), s.entryGuardId(),
                s.exitedAt(), s.exitGuardId());
    }
}
