package com.ptit.parking.report.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Port CHỈ ĐỌC: tra cứu hồ sơ sinh viên cho quản lý (thông tin, số điểm, xe, lượt gửi gần đây). */
public interface StudentProfileQuery {

    record StudentRow(long userId, String studentCode, String fullName, String status, long balance,
                      int activeVehicles, boolean parkedNow) {}

    record StudentDetail(long userId, String studentCode, String fullName, String email, String phone, String status,
                         Instant createdAt, long balance) {}

    record VehicleRow(long id, String plateNumber, String vehicleTypeName, String status, Instant registeredAt,
                      boolean parkedNow) {}

    /** {@code exitedAt} null khi xe còn trong bãi; {@code fee} null khi chưa thanh toán. */
    record SessionRow(long id, String plateNumber, Instant enteredAt, Instant exitedAt, Long fee) {}

    /** Tìm theo một đoạn MSSV hoặc họ tên (không phân biệt hoa thường), tối đa {@code limit} kết quả. */
    List<StudentRow> search(String fragment, int limit);

    Optional<StudentDetail> findByCode(String studentCode);

    /** Mọi xe của sinh viên, kể cả xe đã ngừng sử dụng (để tra lịch sử). */
    List<VehicleRow> vehiclesOf(long userId);

    /** Các lượt gửi gần nhất, mới nhất trước. */
    List<SessionRow> recentSessions(long userId, int limit);
}
