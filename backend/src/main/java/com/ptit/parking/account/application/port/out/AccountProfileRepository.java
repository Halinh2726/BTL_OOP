package com.ptit.parking.account.application.port.out;

import com.ptit.parking.account.domain.AccountProfile;
import com.ptit.parking.account.domain.EmailAddress;
import java.util.Optional;

/** Port: đọc / ghi thông tin cá nhân (users + students / guards). Trả về đúng lớp con theo vai trò. */
public interface AccountProfileRepository {

    Optional<AccountProfile> findById(long userId);

    /** Email đã thuộc về một tài khoản KHÁC {@code userId}. */
    boolean emailUsedByOther(EmailAddress email, long userId);

    void save(AccountProfile profile);
}
