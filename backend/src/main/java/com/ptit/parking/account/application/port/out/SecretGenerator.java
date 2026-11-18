package com.ptit.parking.account.application.port.out;

/**
 * Port: sinh chuỗi bí mật ngẫu nhiên. Tách ra interface để test thay bằng giá trị cố định —
 * use case không gọi thẳng {@code SecureRandom}.
 */
public interface SecretGenerator {

    /** Mã OTP đúng 6 chữ số, ví dụ "048213". */
    String oneTimeCode();

    /** Mật khẩu tạm 10 ký tự, không có ký tự dễ nhầm (0/O, 1/l/I). */
    String temporaryPassword();
}
