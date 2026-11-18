package com.ptit.parking.account.domain;

import com.ptit.parking.auth.domain.Role;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Thông tin cá nhân của một tài khoản — lớp TRỪU TƯỢNG, {@code sealed}, 3 lớp con theo vai trò.
 * <p><b>Template Method:</b> {@link #update} là quy trình CHUNG (kiểm tra họ tên, đổi email, rồi xử lý số điện thoại);
 * bước "số điện thoại" khác nhau theo vai trò nên để lớp con tự cài đặt qua {@link #acceptPhone}:
 * sinh viên — không bắt buộc; bảo vệ — bắt buộc (quản lý cần gọi được khi có sự cố); quản lý, chủ bãi — không lưu.
 * <p>Không có cách nào đổi tên đăng nhập hay MSSV: đó là căn cứ đối chiếu thẻ và truy vết.
 */
public abstract sealed class AccountProfile permits StudentProfile, GuardProfile, StaffProfile {

    public static final int MAX_FULL_NAME = 100;

    private final long userId;
    private final String username;
    private String fullName;
    private EmailAddress email;                 // null: chưa khai báo email

    protected AccountProfile(long userId, String username, String fullName, EmailAddress email) {
        this.userId = userId;
        this.username = Objects.requireNonNull(username, "username");
        this.fullName = requireFullName(fullName);
        this.email = email;
    }

    public abstract Role role();

    public abstract Optional<PhoneNumber> phone();

    /** Bước riêng của từng vai trò trong {@link #update}. */
    protected abstract boolean acceptPhone(PhoneNumber phone);

    /** Chỉ sinh viên có MSSV. */
    public Optional<String> studentCode() {
        return Optional.empty();
    }

    /**
     * Cập nhật họ tên, email, số điện thoại (email/số điện thoại {@code null} = xóa).
     *
     * @return tên các trường thực sự thay đổi — để ghi nhật ký; rỗng nếu không đổi gì
     */
    public final List<String> update(String newFullName, EmailAddress newEmail, PhoneNumber newPhone) {
        String name = requireFullName(newFullName);
        List<String> changed = new ArrayList<>();
        if (!name.equals(fullName)) {
            fullName = name;
            changed.add("họ tên");
        }
        if (!Objects.equals(newEmail, email)) {
            email = newEmail;
            changed.add("email");
        }
        if (acceptPhone(newPhone)) {
            changed.add("số điện thoại");
        }
        return List.copyOf(changed);
    }

    public long userId() { return userId; }

    public String username() { return username; }

    public String fullName() { return fullName; }

    public Optional<EmailAddress> email() { return Optional.ofNullable(email); }

    private static String requireFullName(String value) {
        String name = value == null ? "" : value.strip().replaceAll("\\s+", " ");
        if (name.isEmpty() || name.length() > MAX_FULL_NAME) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR);
        }
        return name;
    }
}
