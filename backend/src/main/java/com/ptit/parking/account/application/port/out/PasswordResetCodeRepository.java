package com.ptit.parking.account.application.port.out;

import com.ptit.parking.account.domain.PasswordResetCode;
import java.util.Optional;

/** Port: mã OTP khôi phục mật khẩu (password_reset_codes). Chỉ mã MỚI NHẤT của một người còn ý nghĩa. */
public interface PasswordResetCodeRepository {

    /** Mã chưa có id → thêm mới; đã có id → ghi số lần sai và thời điểm dùng. */
    void save(PasswordResetCode code);

    /** Mã mới nhất, chỉ đọc. */
    Optional<PasswordResetCode> findLatest(long userId);

    /**
     * Mã mới nhất, KHÓA dòng tới hết transaction: hai lần thử mã song song phải xếp hàng, nên không lách được
     * giới hạn 5 lần sai. Phải gọi BÊN TRONG transaction.
     */
    Optional<PasswordResetCode> lockLatest(long userId);
}
