package com.ptit.parking.shared.security;

import com.ptit.parking.shared.domain.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Phiên tồn tại TỐI ĐA 12 giờ kể từ lúc đăng nhập — đủ một ca trực của bảo vệ (Đặc tả API mục 3).
 * (Giới hạn 30 phút không hoạt động do server.servlet.session.timeout lo.)
 */
public class SessionLifetimeFilter extends OncePerRequestFilter {

    /** Thuộc tính phiên ghi thời điểm đăng nhập; được đặt lúc đăng nhập thành công. */
    public static final String SIGNED_IN_AT = "parking.signedInAt";
    public static final Duration MAX_LIFETIME = Duration.ofHours(12);

    private final Clock clock;
    private final JsonSecurityErrorHandler errors;

    public SessionLifetimeFilter(Clock clock, JsonSecurityErrorHandler errors) {
        this.clock = clock;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null
                && session.getAttribute(SIGNED_IN_AT) instanceof Instant signedInAt
                && clock.instant().isAfter(signedInAt.plus(MAX_LIFETIME))) {
            SessionTermination.end(request);
            errors.writeError(response, ErrorCode.UNAUTHENTICATED);
            return;
        }
        chain.doFilter(request, response);
    }
}
