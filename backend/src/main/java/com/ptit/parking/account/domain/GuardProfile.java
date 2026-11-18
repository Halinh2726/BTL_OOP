package com.ptit.parking.account.domain;

import com.ptit.parking.auth.domain.Role;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.util.Objects;
import java.util.Optional;

/** Hồ sơ bảo vệ: số điện thoại BẮT BUỘC (khớp guards.phone NOT NULL) — quản lý phải liên lạc được khi có sự cố. */
public final class GuardProfile extends AccountProfile {

    private PhoneNumber phone;

    public GuardProfile(long userId, String username, String fullName, EmailAddress email, PhoneNumber phone) {
        super(userId, username, fullName, email);
        this.phone = Objects.requireNonNull(phone, "phone");
    }

    @Override
    public Role role() { return Role.BAO_VE; }

    @Override
    public Optional<PhoneNumber> phone() { return Optional.of(phone); }

    @Override
    protected boolean acceptPhone(PhoneNumber newPhone) {
        if (newPhone == null) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR);
        }
        if (newPhone.equals(phone)) {
            return false;
        }
        phone = newPhone;
        return true;
    }
}
