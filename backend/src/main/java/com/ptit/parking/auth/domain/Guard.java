package com.ptit.parking.auth.domain;

/** BaoVe — có số điện thoại; do quản lý tạo (QL03), chỉ bị khóa chứ không bị xóa (R14). */
public final class Guard extends User {

    private final String phone;

    public Guard(Long id, String username, String passwordHash, String fullName,
                 AccountStatus status, String phone) {
        super(id, username, passwordHash, fullName, status);
        this.phone = requireText(phone, "phone").strip();
    }

    /** QL03: tài khoản bảo vệ mới luôn HOAT_DONG. */
    public static Guard create(String username, String passwordHash, String fullName, String phone) {
        return new Guard(null, username, passwordHash, fullName, AccountStatus.HOAT_DONG, phone);
    }

    @Override
    public Role role() { return Role.BAO_VE; }

    public String phone() { return phone; }
}
