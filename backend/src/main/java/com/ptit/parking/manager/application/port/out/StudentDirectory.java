package com.ptit.parking.manager.application.port.out;

import java.util.Locale;
import java.util.Optional;

/** Port: tra cứu sinh viên theo MSSV cho nghiệp vụ của quản lý (họ tên để đối chiếu thẻ — R08, và ví điểm). */
public interface StudentDirectory {

    record StudentAccount(long userId, String studentCode, String fullName, long walletId) {}

    Optional<StudentAccount> findByStudentCode(String studentCode);

    /** MSSV lưu viết hoa (SV01); quản lý gõ chữ thường hay thừa khoảng trắng vẫn tìm được. */
    static String normalize(String studentCode) {
        return studentCode == null ? "" : studentCode.strip().toUpperCase(Locale.ROOT);
    }
}