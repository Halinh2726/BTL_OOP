package com.ptit.parking.account.domain;

import com.ptit.parking.auth.domain.AccountStatus;
import com.ptit.parking.auth.domain.PasswordHasher;
import com.ptit.parking.auth.domain.Role;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Mật khẩu của MỘT tài khoản và mọi cách thay đổi nó. <b>Đóng gói:</b> không có setter cho hash — chỉ có 3 hành vi
 * nghiệp vụ, mỗi hành vi đòi một "bằng chứng" khác nhau rằng người thao tác được phép:
 * <ul>
 *   <li>{@link #change} — biết mật khẩu hiện tại (người dùng tự đổi).</li>
 *   <li>{@link #reset} — đã nhập đúng mã OTP gửi về email (use case kiểm tra mã trước khi gọi).</li>
 *   <li>{@link #issueTemporary} — quản lý đã đối chiếu thẻ; mật khẩu tạm bắt buộc phải đổi ở lần đăng nhập sau.</li>
 * </ul>
 * Cả ba đều ghi lại thời điểm đổi: phiên đăng nhập trước thời điểm đó bị hủy (CredentialFreshnessFilter).
 */
public final class AccountCredential {

    private final long userId;
    private final String username;
    private final String fullName;
    private final Role role;
    private final AccountStatus status;
    private String passwordHash;
    private boolean mustChangePassword;
    private Instant passwordChangedAt;          // null: chưa đổi lần nào kể từ khi tạo tài khoản

    public AccountCredential(long userId, String username, String fullName, Role role, AccountStatus status,
                             String passwordHash, boolean mustChangePassword, Instant passwordChangedAt) {
        this.userId = userId;
        this.username = Objects.requireNonNull(username, "username");
        this.fullName = Objects.requireNonNull(fullName, "fullName");
        this.role = Objects.requireNonNull(role, "role");
        this.status = Objects.requireNonNull(status, "status");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.mustChangePassword = mustChangePassword;
        this.passwordChangedAt = passwordChangedAt;
    }

    /** Người dùng tự đổi: phải đúng mật khẩu hiện tại; mật khẩu mới theo {@link PasswordPolicy} và khác mật khẩu cũ. */
    public void change(String currentPassword, String newPassword, PasswordHasher hasher, Instant now) {
        if (currentPassword == null || !hasher.matches(currentPassword, passwordHash)) {
            throw new DomainException(ErrorCode.CURRENT_PASSWORD_INCORRECT);
        }
        replaceWith(newPassword, hasher, now);
    }

    /** Quên mật khẩu: gọi SAU khi mã OTP đã được xác nhận đúng. */
    public void reset(String newPassword, PasswordHasher hasher, Instant now) {
        replaceWith(newPassword, hasher, now);
    }

    /**
     * Quản lý cấp mật khẩu tạm sau khi đối chiếu thẻ. Chỉ cho sinh viên và bảo vệ: quản lý không tự cấp lại mật khẩu
     * cho quản lý khác hay cho chủ bãi xe (tách bạch quyền).
     */
    public void issueTemporary(String temporaryPassword, PasswordHasher hasher, Instant now) {
        if (!canBeResetByManager()) {
            throw new DomainException(ErrorCode.FORBIDDEN);
        }
        PasswordPolicy.requireAcceptable(temporaryPassword);
        this.passwordHash = hasher.hash(temporaryPassword);
        this.mustChangePassword = true;
        this.passwordChangedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public boolean canBeResetByManager() {
        return role == Role.SINH_VIEN || role == Role.BAO_VE;
    }

    public boolean isLocked() {
        return status == AccountStatus.BI_KHOA;
    }

    private void replaceWith(String newPassword, PasswordHasher hasher, Instant now) {
        PasswordPolicy.requireAcceptable(newPassword);
        if (hasher.matches(newPassword, passwordHash)) {
            throw new DomainException(ErrorCode.PASSWORD_REUSED);
        }
        this.passwordHash = hasher.hash(newPassword);
        this.mustChangePassword = false;
        this.passwordChangedAt = now.truncatedTo(ChronoUnit.MICROS);   // khớp DATETIME(6)
    }

    public long userId() { return userId; }

    public String username() { return username; }

    public String fullName() { return fullName; }

    public Role role() { return role; }

    public AccountStatus status() { return status; }

    /** Chỉ dùng khi lưu xuống CSDL. */
    public String passwordHash() { return passwordHash; }

    public boolean mustChangePassword() { return mustChangePassword; }

    public Instant passwordChangedAt() { return passwordChangedAt; }
}
