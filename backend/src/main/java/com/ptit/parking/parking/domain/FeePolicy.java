package com.ptit.parking.parking.domain;

import java.time.ZonedDateTime;

/**
 * ChinhSachGia — "tính phí gửi xe" là một HỢP ĐỒNG, không phải một công thức cố định (mẫu Strategy).
 * <p>Nơi dùng (xe vào, xe ra) chỉ phụ thuộc interface này. Muốn đổi cách tính — theo loại xe, theo thời
 * lượng gửi... — chỉ cần thêm một lớp cài đặt mới, KHÔNG sửa code xe vào/xe ra (nguyên lý Open/Closed).
 */
public interface FeePolicy {

    /** R03: phí (điểm) của một lượt gửi, tính theo thời điểm xe RA. */
    long feeAt(ZonedDateTime exitTime);

    /** R04: mức phí thấp nhất — khi xe VÀO chỉ kiểm tra sinh viên có ít nhất chừng này điểm, không trừ. */
    long minimumFee();
}