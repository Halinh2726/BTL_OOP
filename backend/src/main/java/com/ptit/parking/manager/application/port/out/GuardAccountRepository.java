package com.ptit.parking.manager.application.port.out;

import com.ptit.parking.auth.domain.Guard;

/** Port: tài khoản bảo vệ (bảng users + guards) cho nghiệp vụ quản lý bảo vệ QL03, QL04. */
public interface GuardAccountRepository {

    boolean usernameExists(String username);

    /** Lưu bảo vệ CHƯA có id; trả về bảo vệ đã có id do CSDL sinh. */
    Guard save(Guard guard);
}
