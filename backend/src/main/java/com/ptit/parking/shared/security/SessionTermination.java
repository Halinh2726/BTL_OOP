package com.ptit.parking.shared.security;

import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** Hủy phiên hiện tại: dùng chung cho các bộ lọc khi phải "đá" người dùng ra. */
final class SessionTermination {

    private SessionTermination() {
    }

    static void end(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
