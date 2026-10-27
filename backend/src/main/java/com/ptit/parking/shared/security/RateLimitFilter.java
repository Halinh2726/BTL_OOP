package com.ptit.parking.shared.security;

import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.shared.ratelimit.RateLimiter;
import com.ptit.parking.shared.ratelimit.TooManyRequestsException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Giới hạn tần suất (Đặc tả API mục 11), vượt thì 429 TOO_MANY_REQUESTS kèm header Retry-After:
 * <ul>
 *   <li>POST /students/register: 10 lần mỗi giờ mỗi IP — chặn tạo tài khoản rác hàng loạt.</li>
 *   <li>Các API khác: 120 request mỗi phút mỗi phiên.</li>
 * </ul>
 * Đăng nhập (5 lần SAI mỗi phút cho mỗi IP + tên đăng nhập) cần biết tên đăng nhập và kết quả,
 * nên do LoginThrottle ở module auth đảm nhận, không đếm ở đây.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    static final int REGISTER_LIMIT = 10;
    static final int SESSION_LIMIT = 120;

    private final RateLimiter registrations;
    private final RateLimiter sessions;
    private final JsonSecurityErrorHandler errors;

    public RateLimitFilter(Clock clock, JsonSecurityErrorHandler errors) {
        this.registrations = new RateLimiter(REGISTER_LIMIT, Duration.ofHours(1), clock);
        this.sessions = new RateLimiter(SESSION_LIMIT, Duration.ofMinutes(1), clock);
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<Duration> wait = check(request);
        if (wait.isPresent()) {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(TooManyRequestsException.toSeconds(wait.get())));
            errors.writeError(response, ErrorCode.TOO_MANY_REQUESTS);
            return;
        }
        chain.doFilter(request, response);
    }

    private Optional<Duration> check(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (SecurityConfig.REGISTER_URL.equals(path)) {
            return "POST".equals(request.getMethod())
                    ? registrations.tryAcquire(request.getRemoteAddr())
                    : Optional.empty();
        }
        if (SecurityConfig.LOGIN_URL.equals(path) || !path.startsWith("/api/")) {
            return Optional.empty();                         // đăng nhập: LoginThrottle; file tĩnh: không giới hạn
        }
        HttpSession session = request.getSession(false);
        return session == null ? Optional.empty() : sessions.tryAcquire(session.getId());
    }
}
