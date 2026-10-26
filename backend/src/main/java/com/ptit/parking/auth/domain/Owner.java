package com.ptit.parking.auth.domain;

/** ChuTaiKhoan — tạo sẵn trong CSDL, sở hữu ví nhận phí gửi xe. */
public final class Owner extends User {

    public Owner(Long id, String username, String passwordHash, String fullName, AccountStatus status) {
        super(id, username, passwordHash, fullName, status);
    }

    @Override
    public Role role() { return Role.CHU_TAI_KHOAN; }
}
