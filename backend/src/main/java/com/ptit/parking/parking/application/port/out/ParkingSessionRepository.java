package com.ptit.parking.parking.application.port.out;

import com.ptit.parking.parking.domain.ParkingSession;

/** Port: lượt gửi xe (bảng parking_sessions). */
public interface ParkingSessionRepository {

    /** R05: xe này đang có lượt gửi CHƯA ra (DANG_GUI) hay không. */
    boolean hasOpenSession(long vehicleId);

    /**
     * Lưu lượt gửi MỚI, trả về bản có id. Xe đã có lượt đang mở (hai request song song lọt qua bước kiểm tra)
     * thì ràng buộc của CSDL chặn lại và ném {@code DomainException(SESSION_ALREADY_OPEN)} — R05 được giữ
     * ở tầng cuối cùng, kể cả khi code phía trên có sai sót.
     */
    ParkingSession save(ParkingSession session);
}
