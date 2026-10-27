package com.ptit.parking.student.adapter.in.web;

import com.ptit.parking.student.domain.PlateNumber;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body của POST /me/vehicles (Đặc tả API #6). KHÔNG có trường studentId: chủ xe luôn là người đang đăng nhập;
 * client có gửi kèm studentId thì cũng bị bỏ qua.
 */
record RegisterVehicleRequest(
        @NotBlank(message = "Vui lòng nhập biển số")
        @Pattern(regexp = PlateNumber.PATTERN, message = "Biển số 5–20 ký tự, chỉ gồm chữ và số")
        String plateNumber,

        @NotBlank(message = "Vui lòng chọn loại xe")
        @Size(max = 20, message = "Mã loại xe tối đa 20 ký tự")
        String vehicleTypeCode) {

    /** Chuẩn hóa biển số TRƯỚC khi kiểm tra định dạng — dùng chung quy tắc với domain {@link PlateNumber}. */
    RegisterVehicleRequest {
        plateNumber = PlateNumber.normalize(plateNumber);
    }
}