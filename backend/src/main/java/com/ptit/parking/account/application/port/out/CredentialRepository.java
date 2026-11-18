package com.ptit.parking.account.application.port.out;

import com.ptit.parking.account.domain.AccountCredential;
import com.ptit.parking.account.domain.EmailAddress;
import java.util.Optional;

/** Port: mật khẩu của tài khoản (users.password_hash, must_change_password, password_changed_at). */
public interface CredentialRepository {

    Optional<AccountCredential> findByUserId(long userId);

    Optional<AccountCredential> findByEmail(EmailAddress email);

    /** MSSV đã chuẩn hóa (viết hoa). */
    Optional<AccountCredential> findByStudentCode(String studentCode);

    void save(AccountCredential credential);
}
