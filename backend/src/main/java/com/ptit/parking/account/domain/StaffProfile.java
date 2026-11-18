package com.ptit.parking.account.domain;

import com.ptit.parking.auth.domain.Role;
import java.util.Optional;

/** Hồ sơ quản lý hoặc chủ bãi xe: chỉ họ tên và email; hệ thống không lưu số điện thoại của hai vai trò này. */
public final class StaffProfile extends AccountProfile {

    private final Role role;

    public StaffProfile(long userId, String username, String fullName, EmailAddress email, Role role) {
        super(userId, username, fullName, email);
        if (role != Role.QUAN_LY && role != Role.CHU_TAI_KHOAN) {
            throw new IllegalArgumentException("StaffProfile chỉ dành cho quản lý và chủ bãi xe: " + role);
        }
        this.role = role;
    }

    @Override
    public Role role() { return role; }

    @Override
    public Optional<PhoneNumber> phone() { return Optional.empty(); }

    /** Không lưu số điện thoại: giá trị gửi lên (nếu có) bị bỏ qua. */
    @Override
    protected boolean acceptPhone(PhoneNumber phone) {
        return false;
    }
}
