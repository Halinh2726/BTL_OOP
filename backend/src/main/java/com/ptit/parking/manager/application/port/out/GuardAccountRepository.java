package com.ptit.parking.manager.application.port.out;

import com.ptit.parking.auth.domain.Guard;
import java.util.List;
import java.util.Optional;

/** Port: tài khoản bảo vệ (bảng users + guards) cho nghiệp vụ quản lý bảo vệ QL03, QL04. */
public interface GuardAccountRepository {

    boolean usernameExists(String username);

    /** Lưu bảo vệ CHƯA có id; trả về bảo vệ đã có id do CSDL sinh. */
    Guard save(Guard guard);

    /** Mọi bảo vệ, kể cả đang bị khóa, sắp xếp theo họ tên (Đặc tả API #20). */
    List<Guard> findAll();

    /** Rỗng nếu id không phải của một bảo vệ — kể cả khi đó là id của quản lý, sinh viên... */
    Optional<Guard> findById(long userId);

    /** Ghi trạng thái hiện tại của bảo vệ (users.status). */
    void saveStatus(Guard guard);
}
