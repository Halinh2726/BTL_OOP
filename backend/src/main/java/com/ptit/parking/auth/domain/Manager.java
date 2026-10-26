package com.ptit.parking.auth.domain;

/** QuanLy — tạo sẵn trong CSDL, không có trường riêng. */
public final class Manager extends User {

    public Manager(Long id, String username, String passwordHash, String fullName, AccountStatus status) {
        super(id, username, passwordHash, fullName, status);
    }

    @Override
    public Role role() { return Role.QUAN_LY; }
}
