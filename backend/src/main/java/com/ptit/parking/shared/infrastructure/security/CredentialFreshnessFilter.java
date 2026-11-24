package com.ptit.parking.shared.infrastructure.security;

import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.shared.infrastructure.security.CredentialStateLookup.CredentialState;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Hai quy tắc về mật khẩu, kiểm tra ở MỌI request đã đăng nhập (sau {@link ActiveAccountFilter}, trước bước kiểm tra quyền):
 * <ol>
 *   <li><b>Đổi mật khẩu thì các phiên khác bị đăng xuất.</b> Mỗi phiên ghi thời điểm mật khẩu được xác minh
 *       ({@link #CREDENTIALS_VERIFIED_AT}); phiên xác minh TRƯỚC lần đổi mật khẩu gần nhất → 401, phiên bị hủy.</li>
 *   <li><b>Mật khẩu tạm phải đổi ngay.</b> Tài khoản đang dùng mật khẩu tạm chỉ được gọi vài API
 *       ({@link #ALLOWED_WHILE_TEMPORARY}); API khác → 403 PASSWORD_CHANGE_REQUIRED.</li>
 * </ol>
 */
public class CredentialFreshnessFilter extends OncePerRequestFilter {

    /** Thuộc tính phiên: thời điểm người dùng chứng minh biết mật khẩu (đăng nhập hoặc vừa đổi mật khẩu). */
    public static final String CREDENTIALS_VERIFIED_AT = "parking.credentialsVerifiedAt";

    /** "METHOD đường-dẫn" được phép khi đang dùng mật khẩu tạm. */
    static final Set<String> ALLOWED_WHILE_TEMPORARY = Set.of(
            "GET /api/v1/me",
            "GET /api/v1/me/profile",
            "POST /api/v1/me/password",
            "POST " + SecurityConfig.LOGOUT_URL);

    private final CredentialStateLookup credentials;
    private final JsonSecurityErrorHandler errors;

    public CredentialFreshnessFilter(CredentialStateLookup credentials, JsonSecurityErrorHandler errors) {
        this.credentials = credentials;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof SessionUser user) {
            CredentialState state = credentials.stateOf(user.id());
            if (isStale(request.getSession(false), state)) {
                SessionTermination.end(request);
                errors.writeError(response, ErrorCode.UNAUTHENTICATED);
                return;
            }
            if (state.mustChangePassword()
                    && !ALLOWED_WHILE_TEMPORARY.contains(request.getMethod() + " " + request.getRequestURI())) {
                errors.writeError(response, ErrorCode.PASSWORD_CHANGE_REQUIRED);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /** Phiên cũ hơn lần đổi mật khẩu gần nhất. Phiên không ghi mốc xác minh thì dùng giờ đăng nhập. */
    private static boolean isStale(HttpSession session, CredentialState state) {
        if (session == null || state.passwordChangedAt() == null) {
            return false;
        }
        Object verified = session.getAttribute(CREDENTIALS_VERIFIED_AT);
        if (!(verified instanceof Instant)) {
            verified = session.getAttribute(SessionLifetimeFilter.SIGNED_IN_AT);
        }
        return verified instanceof Instant verifiedAt && verifiedAt.isBefore(state.passwordChangedAt());
    }
}
