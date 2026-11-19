package com.ptit.parking.parking.application.port.out;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Port (Đợt 5): xe này có vé tháng còn hiệu lực vào ngày {@code day} không. Module parking chỉ cần biết ĐIỀU NÀY,
 * không cần biết vé được mua, lưu, tính giá ra sao (đảo phụ thuộc — module pass cài đặt port này).
 */
public interface PassCoverage {

    /** Ngày cuối cùng vé còn hiệu lực, hoặc rỗng nếu xe không có vé cho ngày đó. */
    Optional<LocalDate> coveredUntil(long vehicleId, LocalDate day);
}
