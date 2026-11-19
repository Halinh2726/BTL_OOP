package com.ptit.parking.gate.domain;

import com.ptit.parking.student.domain.PlateNumber;
import java.time.Duration;
import java.time.Instant;

/**
 * Một xe đang trong bãi, nhìn từ góc bảo vệ (read model). Không có số điểm của sinh viên (R06).
 * Quy tắc "gửi quá lâu" nằm cạnh dữ liệu của nó: {@link #isOverdue}.
 */
public record ParkedVehicle(long sessionId, PlateNumber plate, String vehicleTypeCode, String studentCode,
                            String fullName, Instant enteredAt, String entryGuardName) {

    /** Gửi quá 24 giờ → tô nổi bật trên danh sách, gợi ý bảo vệ báo sự cố "xe gửi quá lâu". */
    public static final Duration OVERDUE_AFTER = Duration.ofHours(24);

    public Duration parkedFor(Instant now) {
        return Duration.between(enteredAt, now).isNegative() ? Duration.ZERO : Duration.between(enteredAt, now);
    }

    public boolean isOverdue(Instant now) {
        return parkedFor(now).compareTo(OVERDUE_AFTER) > 0;
    }
}
