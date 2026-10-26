package com.ptit.parking.auth.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body của POST /auth/login (Đặc tả API #1): username 1–50 ký tự, password 1–72 ký tự. */
record LoginRequest(
        @NotBlank(message = "Vui lòng nhập tên đăng nhập")
        @Size(max = 50, message = "Tên đăng nhập tối đa 50 ký tự")
        String username,

        @NotBlank(message = "Vui lòng nhập mật khẩu")
        @Size(max = 72, message = "Mật khẩu tối đa 72 ký tự")
        String password) {
}