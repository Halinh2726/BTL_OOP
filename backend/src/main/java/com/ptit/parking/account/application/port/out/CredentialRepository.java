package com.ptit.parking.account.application.port.out;

import com.ptit.parking.account.domain.AccountCredential;
import com.ptit.parking.account.domain.EmailAddress;
import java.time.Instant;
import java.util.Optional;

/** Port: mật khẩu của tài khoản (users.password_hash, must_change_password, password_changed_at). */
public interface CredentialRepository {

    Optional<AccountCredential> findByUserId(long userId);

    Optional<AccountCredential> findByEmail(EmailAddress email);

    /** MSSV đã chuẩn hóa (viết hoa). */
    Optional<AccountCredential> findByStudentCode(String studentCode);

    /** Tìm theo tên đăng nhập (chữ thường). */
    default Optional<AccountCredential> findByUsername(String username) {
        return Optional.empty();
    }

    /** Lấy email đã đăng ký (nếu có) theo userId. */
    default Optional<EmailAddress> findEmailByUserId(long userId) {
        return Optional.empty();
    }

    void save(AccountCredential credential);

    /**
     * CHỈ dùng cho môi trường demo: ghi cùng một hash mật khẩu cho MỌI tài khoản, bỏ cờ mật khẩu tạm.
     *
     * @return số tài khoản đã đặt lại
     */
    int resetAllPasswords(String passwordHash, Instant changedAt);
}
