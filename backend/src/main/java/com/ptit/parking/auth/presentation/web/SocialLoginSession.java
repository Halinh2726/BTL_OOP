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
        request.getSession().setAttribute(KEY,
                new Pending(UUID.randomUUID(), identity, userId, clock.instant().plus(TTL)));
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
        Pending pending = find(request, userId);
        if (pending != null && pending.id().equals(id)) {
            request.getSession().removeAttribute(KEY);
        }
    }
}
