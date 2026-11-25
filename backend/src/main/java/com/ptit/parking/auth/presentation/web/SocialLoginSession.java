package com.ptit.parking.auth.presentation.web;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class SocialLoginSession {

    private static final String KEY = SocialLoginSession.class.getName();
    private static final Duration TTL = Duration.ofMinutes(10);

    record Identity(String provider, String subject, String fullName, String label, String recoveryEmail)
            implements Serializable {}

    record Pending(UUID id, Identity identity, Long userId, Instant expiresAt) implements Serializable {}

    private final Clock clock;

    SocialLoginSession(Clock clock) {
        this.clock = clock;
    }

    void save(HttpServletRequest request, Identity identity, Long userId) {
        HttpSession session = request.getSession();
        synchronized (session) {
            session.setAttribute(KEY, new Pending(UUID.randomUUID(), identity, userId, clock.instant().plus(TTL)));
        }
    }

    /** Chuyển phiên OAuth đã xác minh sang tài khoản vừa đăng nhập, giữ nguyên hạn xác nhận. */
    void prepareLink(HttpServletRequest request, UUID id, long userId) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        synchronized (session) {
            Pending pending = find(request, null);
            if (pending != null && pending.id().equals(id)) {
                session.setAttribute(KEY, new Pending(id, pending.identity(), userId, pending.expiresAt()));
            }
        }
    }

    Pending find(HttpServletRequest request, Long userId) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(KEY) instanceof Pending pending)
                || !pending.expiresAt().isAfter(clock.instant())
                || !Objects.equals(userId, pending.userId())) {
            return null;
        }
        return pending;
    }

    Pending require(HttpServletRequest request, UUID id, Long userId) {
        Pending pending = find(request, userId);
        if (pending == null || !pending.id().equals(id)) {
            throw new DomainException(ErrorCode.SOCIAL_FLOW_EXPIRED);
        }
        return pending;
    }

    void clear(HttpServletRequest request, UUID id, Long userId) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        synchronized (session) {
            Pending pending = find(request, userId);
            if (pending != null && pending.id().equals(id)) {
                session.removeAttribute(KEY);
            }
        }
    }
}
