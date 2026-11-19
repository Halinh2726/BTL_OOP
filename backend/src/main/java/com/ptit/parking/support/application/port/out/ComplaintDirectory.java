package com.ptit.parking.support.application.port.out;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Port CHỈ ĐỌC: thông tin hiển thị kèm khiếu nại — tên người, MSSV, lượt gửi liên quan và phí đã thu. */
public interface ComplaintDirectory {

    record StudentInfo(long userId, String studentCode, String fullName) {}

    /** {@code fee}, {@code paymentTransactionId} null khi lượt gửi chưa thanh toán (xe còn trong bãi). */
    record SessionInfo(long id, long studentId, String plateNumber, Instant enteredAt, Instant exitedAt, Long fee,
                       Long paymentTransactionId) {}

    Map<Long, String> namesOf(Collection<Long> userIds);

    Map<Long, StudentInfo> studentsOf(Collection<Long> userIds);

    Map<Long, SessionInfo> sessionsOf(Collection<Long> sessionIds);

    /** Lượt gửi của CHÍNH sinh viên này; của người khác → rỗng. */
    Optional<SessionInfo> sessionOfStudent(long sessionId, long studentId);

    /** Các lượt gửi gần nhất của sinh viên — để chọn khi gửi khiếu nại. */
    List<SessionInfo> recentSessions(long studentId, int limit);
}
