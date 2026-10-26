package com.ptit.parking.shared.security;

import com.ptit.parking.shared.domain.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Kiểm tra trạng thái tài khoản ở MỌI request đã đăng nhập (Đặc tả API mục 3, QL04, R14):
 * quản lý vừa khóa bảo vệ thì request KẾ TIẾP của bảo vệ đó bị từ chối và phiên bị hủy ngay,
 * dù phiên chưa hết hạn. Chạy TRƯỚC bước kiểm tra quyền (thứ tự lỗi: 401 → 403 ACCOUNT_LOCKED → 403 FORBIDDEN).
 */
public class ActiveAccountFilter extends OncePerRequestFilter {

    private final AccountStatusLookup accounts;
    private final JsonSecurityErrorHandler errors;

    public ActiveAccountFilter(AccountStatusLookup accounts, JsonSecurityErrorHandler errors) {
        this.accounts = accounts;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof SessionUser user) {
            switch (accounts.stateOf(user.id())) {
                case LOCKED -> {
                    SessionTermination.end(request);
                    errors.writeError(response, ErrorCode.ACCOUNT_LOCKED);
                    return;
                }
                case NOT_FOUND -> {
                    SessionTermination.end(request);
                    errors.writeError(response, ErrorCode.UNAUTHENTICATED);
                    return;
                }
                case ACTIVE -> { /* tiếp tục */ }
            }
        }
        chain.doFilter(request, response);
    }
}
