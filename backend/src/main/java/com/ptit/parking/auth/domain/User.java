package com.ptit.parking.auth.domain;

import java.util.Objects;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;

/**
 * NguoiDung — lớp TRỪU TƯỢNG gốc của 4 vai trò.
 * <ul>
 *   <li>Trừu tượng hóa: không thể {@code new User(...)}, phải là một vai trò cụ thể.</li>
 *   <li>Kế thừa: trường chung (id, username, fullName, status) khai báo MỘT lần ở đây.</li>
 *   <li>Đóng gói: field private; status chỉ đổi qua {@link #lock()} / {@link #unlock()}.</li>
 * </ul>
 */
public abstract class User {

    private final Long id;                 // null khi chưa lưu vào CSDL
    private final String username;
    private final String passwordHash;
    private final String fullName;
    private AccountStatus status;          // không final: trạng thái DUY NHẤT được phép thay đổi

    protected User(Long id, String username, String passwordHash, String fullName, AccountStatus status) {
        this.id = id;
        this.username = requireText(username, "username");
        this.passwordHash = requireText(passwordHash, "passwordHash");
        this.fullName = requireText(fullName, "fullName").strip();
        this.status = Objects.requireNonNull(status, "status");
    }

    /** Phương thức trừu tượng: mỗi lớp con BẮT BUỘC override để cho biết vai trò của mình. */
    public abstract Role role();

    /** C01: so khớp mật khẩu qua interface — User không biết BCrypt. */
    public boolean passwordMatches(String rawPassword, PasswordHasher hasher) {
        return hasher.matches(rawPassword, passwordHash);
    }

    /** C01: tài khoản bị khóa không được đăng nhập hay gọi API. */
    public void ensureActive() {
        if (isLocked()) {
            throw new DomainException(ErrorCode.ACCOUNT_LOCKED);
        }
    }

    public void lock() { status = AccountStatus.BI_KHOA; }

    public void unlock() { status = AccountStatus.HOAT_DONG; }

    public boolean isLocked() { return status == AccountStatus.BI_KHOA; }

    // ----- Getter (KHÔNG có setter) -----
    public Long id() { return id; }

    public String username() { return username; }

    public String fullName() { return fullName; }

    public AccountStatus status() { return status; }

    /** Chỉ dùng khi lưu xuống CSDL; không bao giờ trả ra API. */
    public String passwordHash() { return passwordHash; }

    protected static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " không được để trống");
        }
        return value;
    }
}
