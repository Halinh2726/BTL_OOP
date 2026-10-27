package com.ptit.parking.manager.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body của POST /manager/guards (Đặc tả API #21). */
record CreateGuardRequest(
        @NotBlank(message = "Vui lòng nhập họ tên")
        @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
        String fullName,

        @NotBlank(message = "Vui lòng nhập số điện thoại")
        @Pattern(regexp = "^0[0-9]{9,10}$", message = "Số điện thoại gồm 10–11 chữ số, bắt đầu bằng 0")
        String phone,

        @NotBlank(message = "Vui lòng nhập tên đăng nhập")
        @Pattern(regexp = "^[a-z0-9._]{4,50}$",
                message = "Tên đăng nhập 4–50 ký tự, chỉ gồm chữ thường, số, dấu chấm, gạch dưới")
        String username,

        @NotBlank(message = "Vui lòng nhập mật khẩu")
        @Size(min = 8, max = 72, message = "Mật khẩu từ 8 đến 72 ký tự")
        String password) {

    /** Bỏ khoảng trắng đầu cuối của họ tên và số điện thoại trước khi kiểm tra. */
    CreateGuardRequest {
        fullName = fullName == null ? null : fullName.strip();
        phone = phone == null ? null : phone.strip();
    }
}