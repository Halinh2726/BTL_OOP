package com.ptit.parking.account.domain;

import com.ptit.parking.auth.domain.Role;
import java.util.Objects;
import java.util.Optional;

/** Hồ sơ sinh viên: có MSSV (không sửa được); số điện thoại không bắt buộc. */
public final class StudentProfile extends AccountProfile {

    private final String studentCode;
    private PhoneNumber phone;

    public StudentProfile(long userId, String username, String fullName, EmailAddress email,
                          String studentCode, PhoneNumber phone) {
        super(userId, username, fullName, email);
        this.studentCode = Objects.requireNonNull(studentCode, "studentCode");
        this.phone = phone;
    }

    @Override
    public Role role() { return Role.SINH_VIEN; }

    @Override
    public Optional<PhoneNumber> phone() { return Optional.ofNullable(phone); }

    @Override
    public Optional<String> studentCode() { return Optional.of(studentCode); }

    @Override
    protected boolean acceptPhone(PhoneNumber newPhone) {
        if (Objects.equals(newPhone, phone)) {
            return false;
        }
        phone = newPhone;
        return true;
    }
}
