package com.ptit.parking.parking.application.port.out;

import com.ptit.parking.parking.domain.ParkingSession;
import java.util.Optional;

/** Port: lượt gửi xe (bảng parking_sessions). */
public interface ParkingSessionRepository {

    /** R05: xe này đang có lượt gửi CHƯA ra (DANG_GUI) hay không. */
    boolean hasOpenSession(long vehicleId);

    
    /** Lượt DANG_GUI của xe (nếu có). Chỉ đọc, không khóa — dùng cho tra cứu khi xe ra. */
    Optional<ParkingSession> findOpenByVehicle(long vehicleId);

    /**
     * Lưu lượt gửi MỚI, trả về bản có id. Xe đã có lượt đang mở (hai request song song lọt qua bước kiểm tra)
     * thì ràng buộc của CSDL chặn lại và ném {@code DomainException(SESSION_ALREADY_OPEN)} — R05 được giữ
     * ở tầng cuối cùng, kể cả khi code phía trên có sai sót.
     */
    ParkingSession save(ParkingSession session);
    
    /**
     * Lượt gửi theo id, KHÓA dòng (SELECT ... FOR UPDATE) tới hết transaction: hai bảo vệ xác nhận cùng một lượt
     * thì người sau chờ người trước xong, rồi thấy lượt đã đóng. Phải gọi BÊN TRONG transaction.
     */
    Optional<ParkingSession> lockById(long sessionId);

    /** Ghi việc xe ra của một lượt vừa {@link ParkingSession#close đóng} (giờ ra, bảo vệ cho ra, DA_RA). */
    void saveExit(ParkingSession session);

}
