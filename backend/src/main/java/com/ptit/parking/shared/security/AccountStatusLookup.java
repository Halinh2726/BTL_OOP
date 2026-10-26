package com.ptit.parking.shared.security;

/**
 * Port: hỏi trạng thái HIỆN TẠI của một tài khoản trong CSDL. Được gọi ở MỌI request đã đăng nhập
 * (Đặc tả API mục 3) nên chỉ đọc đúng một cột, không nạp cả đối tượng người dùng.
 */
public interface AccountStatusLookup {

    AccountState stateOf(long userId);

    enum AccountState {
        ACTIVE,
        LOCKED,
        NOT_FOUND
    }
}
