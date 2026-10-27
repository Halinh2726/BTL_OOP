package com.ptit.parking.parking.application.port.out;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.ptit.parking.parking.domain.ParkingSession;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;

/**
 * Kho lượt gửi GIẢ trong bộ nhớ, dùng chung cho test các use case xe vào/xe ra.
 * <p>Giống CSDL thật ở chỗ quan trọng nhất: mỗi xe nhiều nhất MỘT lượt đang mở (như uq_parking_sessions_open).
 * Các hàm {@code synchronized} để test nhiều luồng dùng được.
 */
public class InMemoryParkingSessionRepository implements ParkingSessionRepository {

    private final Set<Long> openVehicles = new HashSet<>();
    private final List<ParkingSession> saved = new ArrayList<>();
    private long nextId = 1;

    @Override
    public synchronized boolean hasOpenSession(long vehicleId) {
        return openVehicles.contains(vehicleId);
    }

    @Override
    public synchronized ParkingSession save(ParkingSession session) {
        if (session.isOpen() && !openVehicles.add(session.vehicleId())) {
            throw new DomainException(ErrorCode.SESSION_ALREADY_OPEN);      // như ràng buộc UNIQUE của CSDL
        }
        ParkingSession withId = session.withId(nextId++);
        saved.add(withId);
        return withId;
    }

    /** Chuẩn bị dữ liệu test: xe này đang có lượt gửi chưa ra. */
    public synchronized void markOpen(long vehicleId) {
        openVehicles.add(vehicleId);
    }

    /** Các lượt đã lưu qua {@link #save}. */
    public synchronized List<ParkingSession> all() {
        return List.copyOf(saved);
    }
}
