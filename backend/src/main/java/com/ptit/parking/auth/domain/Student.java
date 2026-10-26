package com.ptit.parking.auth.domain;

import java.util.Locale;

/** SinhVien — chỉ sinh viên mới có MSSV (trình biên dịch tự đảm bảo, không cần if kiểm tra). */
public final class Student extends User {

    private final String studentCode;

    public Student(Long id, String username, String passwordHash, String fullName,
                   AccountStatus status, String studentCode) {
        super(id, username, passwordHash, fullName, status);
        this.studentCode = requireText(studentCode, "studentCode").strip().toUpperCase(Locale.ROOT);
    }

    /** SV01: sinh viên tự đăng ký -> chưa có id, luôn HOAT_DONG. */
    public static Student register(String username, String passwordHash, String fullName, String studentCode) {
        return new Student(null, username, passwordHash, fullName, AccountStatus.HOAT_DONG, studentCode);
    }

    @Override
    public Role role() { return Role.SINH_VIEN; }

    public String studentCode() { return studentCode; }
}
