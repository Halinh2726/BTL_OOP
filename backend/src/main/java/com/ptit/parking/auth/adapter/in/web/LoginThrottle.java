package com.ptit.parking.auth.adapter.in.web;

import com.ptit.parking.shared.ratelimit.RateLimiter;
import com.ptit.parking.shared.ratelimit.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Chống dò mật khẩu (Đặc tả API mục 11): tối đa 5 lần đăng nhập SAI mỗi phút cho mỗi cặp IP + tên đăng nhập.
 * Đếm theo CẶP: kẻ dò mật khẩu một tài khoản bị chặn, người khác cùng mạng (cùng IP) vẫn đăng nhập bình thường.
 * <p>Mỗi lần thử được ghi TRƯỚC khi kiểm tra mật khẩu (gửi song song cũng không lọt quá 5);
 * đăng nhập đúng thì xóa bộ đếm — nên trong bộ đếm chỉ còn những lần sai.
 */
@Component
class LoginThrottle {

    static final int MAX_FAILURES = 5;

    private final RateLimiter attempts;

    LoginThrottle(Clock clock) {
        this.attempts = new RateLimiter(MAX_FAILURES, Duration.ofMinutes(1), clock);
    }

    /** Khóa đếm: IP + tên đăng nhập viết thường (MySQL so tên không phân biệt hoa thường, "QuanLy01" = "quanly01"). */
    static String keyOf(HttpServletRequest request, String username) {
        return request.getRemoteAddr() + "|" + username.strip().toLowerCase(Locale.ROOT);
    }

    /** Gọi trước khi kiểm tra mật khẩu. Đã sai đủ 5 lần trong 1 phút -> 429, kể cả lần này gõ đúng. */
    void beforeAttempt(String key) {
        attempts.tryAcquire(key).ifPresent(wait -> {
            throw new TooManyRequestsException(wait);
        });
    }

    void succeeded(String key) {
        attempts.reset(key);
    }
}
