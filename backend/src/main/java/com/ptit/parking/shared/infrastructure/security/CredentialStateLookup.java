package com.ptit.parking.shared.infrastructure.security;

import java.time.Instant;

/**
 * Port: trạng thái MẬT KHẨU hiện tại của một tài khoản, đọc ở mọi request đã đăng nhập
 * (xem {@link CredentialFreshnessFilter}). Tách khỏi {@link AccountStatusLookup} theo nguyên lý
 * Interface Segregation: khóa/mở khóa và đổi mật khẩu là hai mối quan tâm khác nhau.
 */
public interface CredentialStateLookup {

    /**
     * @param passwordChangedAt  lần đổi mật khẩu gần nhất; {@code null} nếu chưa đổi lần nào
     * @param mustChangePassword đang dùng mật khẩu tạm do quản lý cấp, phải đổi trước khi làm việc khác
     */
    record CredentialState(Instant passwordChangedAt, boolean mustChangePassword) {

        public static final CredentialState UNCHANGED = new CredentialState(null, false);
    }

    CredentialState stateOf(long userId);
}
