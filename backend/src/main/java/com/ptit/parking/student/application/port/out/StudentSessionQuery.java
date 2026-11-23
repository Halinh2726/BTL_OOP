package com.ptit.parking.student.application.port.out;

import java.time.Instant;
import java.util.List;

/** Port chỉ đọc: danh sách lượt gửi xe của chính sinh viên đang đăng nhập. */
public interface StudentSessionQuery {

    record SessionRecord(
            long id,
            String plateNumber,
            String vehicleTypeCode,
            Instant enteredAt,
            Instant exitedAt,
            Long fee,
            Long paymentTransactionId,
            String entryGuardName,
            String exitGuardName) {

        public boolean open() {
            return exitedAt == null;
        }
    }

    List<SessionRecord> findByStudent(long studentId, int limit);
}
