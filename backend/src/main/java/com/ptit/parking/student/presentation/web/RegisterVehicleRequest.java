package com.ptit.parking.student.presentation.web;

import com.ptit.parking.student.domain.PlateNumber;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body của POST /me/vehicles (Đặc tả API #6). KHÔNG có trường studentId: chủ xe luôn là người đang đăng nhập;
 * client có gửi kèm studentId thì cũng bị bỏ qua.
 * <p>Đợt 1: biển số theo quy định (29A-1514, 29A-123.45, 29A1-123.45). Để trống chỉ hợp lệ với xe đạp —
 * bãi tự cấp mã XD-xxxxx (use case kiểm tra, vì cần biết loại xe).
 */
record RegisterVehicleRequest(
        @Pattern(regexp = "^$|" + PlateNumber.STANDARD_PATTERN,
                message = "Biển xe xăng theo dạng 36S4-12345 hoặc 26DC-12345; xe điện theo dạng 29MĐ-123.45")
        String plateNumber,

        @NotBlank(message = "Vui lòng chọn loại xe")
        @Size(max = 20, message = "Mã loại xe tối đa 20 ký tự")
        String vehicleTypeCode) {

    /** Chuẩn hóa biển số TRƯỚC khi kiểm tra định dạng — dùng chung quy tắc với domain {@link PlateNumber}. */
    RegisterVehicleRequest {
        plateNumber = PlateNumber.normalize(plateNumber);
    }
}
