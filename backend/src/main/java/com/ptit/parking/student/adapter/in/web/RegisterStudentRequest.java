package com.ptit.parking.student.adapter.in.web;

import java.util.Locale;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body của POST /students/register (Đặc tả API #4). */
record RegisterStudentRequest(
        @NotBlank(message = "Vui lòng nhập MSSV")
        @Pattern(regexp = "^[A-Z0-9]{1,20}$", message = "MSSV chỉ gồm chữ và số, tối đa 20 ký tự")
        String studentCode,

        @NotBlank(message = "Vui lòng nhập họ tên")
        @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
        String fullName,

        @NotBlank(message = "Vui lòng nhập tên đăng nhập")
        @Pattern(regexp = "^[a-z0-9._]{4,50}$",
                message = "Tên đăng nhập 4–50 ký tự, chỉ gồm chữ thường, số, dấu chấm, gạch dưới")
        String username,

        @NotBlank(message = "Vui lòng nhập mật khẩu")
        @Size(min = 8, max = 72, message = "Mật khẩu từ 8 đến 72 ký tự")
        String password) {

    /** Chuẩn hóa TRƯỚC khi kiểm tra: MSSV viết hoa, bỏ khoảng trắng đầu cuối của MSSV và họ tên. */
    RegisterStudentRequest {
        studentCode = studentCode == null ? null : studentCode.strip().toUpperCase(Locale.ROOT);
        fullName = fullName == null ? null : fullName.strip();
    }
}