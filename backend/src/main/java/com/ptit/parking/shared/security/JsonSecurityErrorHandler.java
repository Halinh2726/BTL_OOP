package com.ptit.parking.shared.security;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.shared.web.ErrorResponse;
import com.ptit.parking.shared.web.TraceIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Trả lỗi bảo mật (401/403) theo ĐÚNG cấu trúc {@link ErrorResponse} như mọi lỗi khác.
 * <p>Một lớp cài đặt HAI interface của Spring Security: {@link AuthenticationEntryPoint}
 * (chưa đăng nhập) và {@link AccessDeniedHandler} (đã đăng nhập nhưng bị từ chối).
 * Spring gọi qua kiểu interface — không cần biết lớp cụ thể là gì (đa hình qua interface).
 */
@Component
public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper json;

    public JsonSecurityErrorHandler(ObjectMapper json) {
        this.json = json;
    }

    /** Chưa đăng nhập hoặc phiên hết hạn -> 401 UNAUTHENTICATED. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        writeError(response, ErrorCode.UNAUTHENTICATED);
    }

    /** Bị từ chối: thiếu/sai CSRF token -> CSRF_INVALID; thiếu quyền -> FORBIDDEN. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        if (ex instanceof CsrfException) {
            // Chưa đăng nhập thì lỗi đúng hơn là 401 (thứ tự kiểm tra: phiên -> ... -> quyền, Đặc tả API mục 4)
            writeError(response, isLoggedIn() ? ErrorCode.CSRF_INVALID : ErrorCode.UNAUTHENTICATED);
        } else {
            writeError(response, ErrorCode.FORBIDDEN);
        }
    }

    private static boolean isLoggedIn() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }

    /** Ghi một lỗi JSON chuẩn; các filter bảo mật (khóa tài khoản, hết hạn phiên) dùng chung. */
    public void writeError(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.httpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), ErrorResponse.of(code, TraceIdFilter.currentTraceId()));
    }
}