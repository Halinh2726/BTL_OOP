package com.ptit.parking.auth.domain;

/**
 * Trừu tượng hóa: domain chỉ biết "băm" và "so khớp" mật khẩu, KHÔNG biết dùng thuật toán gì.
 * Cài đặt thật bằng BCrypt nằm ở tầng adapter (bước 6) -> đổi thuật toán không phải sửa domain.
 */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);
}
