package com.ptit.parking.parking.application.port.out;

/** Port: lượt gửi xe (bảng parking_sessions). */
public interface ParkingSessionRepository {

    /** R05: xe này đang có lượt gửi CHƯA ra (DANG_GUI) hay không. */
    boolean hasOpenSession(long vehicleId);
}