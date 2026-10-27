package com.ptit.parking.parking.application.port.out;

import java.util.HashSet;
import java.util.Set;

/** Kho lượt gửi GIẢ trong bộ nhớ, dùng chung cho test các use case xe vào/xe ra. */
public class InMemoryParkingSessionRepository implements ParkingSessionRepository {

    private final Set<Long> openVehicles = new HashSet<>();

    @Override
    public boolean hasOpenSession(long vehicleId) {
        return openVehicles.contains(vehicleId);
    }

    /** Chuẩn bị dữ liệu test: xe này đang có lượt gửi chưa ra. */
    public void markOpen(long vehicleId) {
        openVehicles.add(vehicleId);
    }
}
