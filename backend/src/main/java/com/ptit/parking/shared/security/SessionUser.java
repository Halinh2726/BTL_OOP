package com.ptit.parking.shared.security;

/**
 * Điều tối thiểu tầng bảo mật cần biết về người đang đăng nhập: mã người dùng.
 * Module auth cài đặt interface này (AuthenticatedUser) — shared không phụ thuộc ngược vào auth.
 */
public interface SessionUser {

    long id();
}