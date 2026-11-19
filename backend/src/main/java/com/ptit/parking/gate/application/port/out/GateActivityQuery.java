package com.ptit.parking.gate.application.port.out;

import com.ptit.parking.gate.domain.ParkedVehicle;
import com.ptit.parking.student.domain.PlateNumber;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Port CHỈ ĐỌC trên dữ liệu lượt gửi (parking_sessions) cho công việc tại cổng. Module gate không ghi lượt gửi —
 * việc đó thuộc module parking — nên tách thành một "query port" riêng (CQRS đơn giản).
 */
public interface GateActivityQuery {

    /** Một lượt bảo vệ đã cho vào hoặc cho ra. */
    record ActivityEntry(long sessionId, PlateNumber plate, String vehicleTypeCode, String studentCode,
                         String fullName, Direction direction, Instant at) {}

    enum Direction { VAO, RA }

    /** Mọi xe đang trong bãi, gửi lâu nhất lên đầu (sức chứa vài trăm xe nên lọc ở tầng ứng dụng). */
    List<ParkedVehicle> parkedVehicles();

    long countParked();

    long countCheckIns(long guardId, Instant from, Instant to);

    long countCheckOuts(long guardId, Instant from, Instant to);

    /** Các lượt bảo vệ này cho vào / cho ra từ {@code from}, mới nhất trước. */
    List<ActivityEntry> activityOf(long guardId, Instant from, int limit);

    /** Họ tên theo id người dùng — để hiển thị ai giao ca, ai nhận ca. */
    Map<Long, String> namesOf(Collection<Long> userIds);
}
