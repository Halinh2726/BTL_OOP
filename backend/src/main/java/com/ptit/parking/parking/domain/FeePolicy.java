package com.ptit.parking.parking.domain;

import java.time.ZonedDateTime;

/**
 * ChinhSachGia — "tính phí gửi xe" là một HỢP ĐỒNG, không phải một công thức cố định (mẫu Strategy).
 * <p>Nơi dùng (xe vào, xe ra) chỉ phụ thuộc interface này. Muốn đổi cách tính — theo loại xe, theo thời
 * lượng gửi... — chỉ cần thêm một lớp cài đặt mới, KHÔNG sửa code xe vào/xe ra (nguyên lý Open/Closed).
 * <p>Đợt 5: thêm hai phương thức theo TỪNG XE ({@link #quote}, {@link #minimumFeeFor}) dạng {@code default} — cách
 * tính cũ ({@link TimeSlotFeePolicy}) không phải sửa một dòng nào; vé tháng là một lớp BỌC bên ngoài (Decorator).
 */
public interface FeePolicy {

    /** R03: phí (điểm) của một lượt gửi, tính theo thời điểm xe RA. */
    long feeAt(ZonedDateTime exitTime);

    /** R04: mức phí thấp nhất — khi xe VÀO chỉ kiểm tra sinh viên có ít nhất chừng này điểm, không trừ. */
    long minimumFee();

    /** Đợt 5 — phí của MỘT xe khi ra lúc {@code exitTime}. Mặc định: như nhau với mọi xe. */
    default FeeQuote quote(long vehicleId, ZonedDateTime exitTime) {
        return FeeQuote.charge(feeAt(exitTime));
    }

    /** Đợt 5 — mức điểm tối thiểu để xe này được VÀO lúc {@code at}. Mặc định: như nhau với mọi xe. */
    default long minimumFeeFor(long vehicleId, ZonedDateTime at) {
        return minimumFee();
    }
}
