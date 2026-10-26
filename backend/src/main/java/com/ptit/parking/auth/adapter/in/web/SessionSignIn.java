package com.ptit.parking.auth.adapter.in.web;

import com.ptit.parking.auth.application.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * Ghi người dùng đã xác thực vào phiên. Tách khỏi controller (SRP): controller lo HTTP,
 * lớp này lo cơ chế phiên của Spring Security.
 */
@Component
class SessionSignIn {

    private final SecurityContextRepository contextRepository;

    SessionSignIn(SecurityContextRepository contextRepository) {
        this.contextRepository = contextRepository;
    }

    void signIn(AuthenticatedUser user, HttpServletRequest request, HttpServletResponse response) {
        // Chống session fixation: nếu đã có phiên cũ thì đổi sang mã phiên mới (Đặc tả API #1 bước 3)
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        // Quyền lấy từ v_user_permissions; thêm ROLE_<vai trò> để dùng khi cần
        List<GrantedAuthority> authorities = new ArrayList<>();
        user.permissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.role().name()));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, authorities));
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }
}