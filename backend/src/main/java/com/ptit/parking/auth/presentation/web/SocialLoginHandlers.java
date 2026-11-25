package com.ptit.parking.auth.presentation.web;

import com.ptit.parking.account.domain.EmailAddress;
import com.ptit.parking.auth.application.AuthenticatedUser;
import com.ptit.parking.auth.application.usecase.StudentSocialSignInUseCase;
import com.ptit.parking.auth.domain.Role;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

@Component
public class SocialLoginHandlers {

    private static final Logger log = LoggerFactory.getLogger(SocialLoginHandlers.class);
    private static final Pattern MICROSOFT_ERROR = Pattern.compile("\\bAADSTS[0-9]{5,8}\\b");
    private final SecurityContextRepository contexts;
    private final StudentSocialSignInUseCase students;
    private final SocialLoginSession pending;
    private final SessionSignIn sessions;

    SocialLoginHandlers(SecurityContextRepository contexts, StudentSocialSignInUseCase students,
                        SocialLoginSession pending, SessionSignIn sessions) {
        this.contexts = contexts;
        this.students = students;
        this.pending = pending;
        this.sessions = sessions;
    }

    public void success(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        AuthenticatedUser localUser = localUser(request);
        try {
            var identity = identity((OAuth2AuthenticationToken) authentication);
            if (localUser != null) {
                if (localUser.role() != Role.SINH_VIEN) {
                    redirect(response, "/account");
                    return;
                }
                pending.save(request, identity, localUser.id());
                redirect(response, "/account?social=confirm");
                return;
            }

            var linked = students.findLinkedUser(identity.provider(), identity.subject());
            if (linked.isPresent()) {
                sessions.signIn(linked.get(), request, response);
                redirect(response, "/student");
            } else {
                pending.save(request, identity, null);
                redirect(response, "/register/social");
            }
        } catch (DomainException e) {
            log.info("Không hoàn tất được đăng nhập OAuth (lỗi ứng dụng={})", e.errorCode());
            String target = e.errorCode() == ErrorCode.ACCOUNT_LOCKED ? "/login?social=locked" : failurePage(localUser);
            redirect(response, target);
        } catch (RuntimeException e) {
            log.warn("Không hoàn tất được đăng nhập qua nhà cung cấp ({})", e.getClass().getSimpleName());
            redirect(response, failurePage(localUser));
        }
    }

    public void failure(HttpServletRequest request, HttpServletResponse response, AuthenticationException error) {
        var oauthError = error instanceof OAuth2AuthenticationException oauth ? oauth.getError() : null;
        String code = oauthError == null ? null : oauthError.getErrorCode();
        boolean denied = "access_denied".equals(code);
        if (!denied && (request.getParameter("code") != null || request.getParameter("error") != null)) {
            var microsoftError = MICROSOFT_ERROR.matcher(oauthError == null || oauthError.getDescription() == null
                    ? "" : oauthError.getDescription());
            String safeCode = code != null && code.matches("[a-z_]{1,45}") ? code : "unknown";
            String provider = request.getRequestURI().endsWith("/microsoft") ? "microsoft" : "google";
            Throwable cause = error.getCause();
            String httpStatus = cause instanceof RestClientResponseException http
                    ? Integer.toString(http.getStatusCode().value()) : "-";
            log.info("OAuth callback bị từ chối (provider={}, lý do={}, Microsoft={}, HTTP={})",
                    provider, safeCode, microsoftError.find() ? microsoftError.group() : "-", httpStatus);
        }
        AuthenticatedUser localUser = localUser(request);
        String page = localUser == null ? "/login" : "/account";
        redirect(response, page + (denied ? "?social=denied" : "?social=failed"));
    }

    private AuthenticatedUser localUser(HttpServletRequest request) {
        Authentication authentication = contexts.loadDeferredContext(request).get().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user ? user : null;
    }

    private static SocialLoginSession.Identity identity(OAuth2AuthenticationToken token) {
        String registrationId = token.getAuthorizedClientRegistrationId();
        String provider = SocialLoginClients.MICROSOFT_PTIT.equals(registrationId) ? "microsoft" : registrationId;
        String subject = token.getName();
        if (subject == null || !subject.matches("[!-~]{1,255}")) {
            throw new DomainException(ErrorCode.INVALID_CREDENTIALS);
        }

        if ("google".equals(provider) && token.getPrincipal() instanceof OidcUser google) {
            var claims = google.getIdToken();
            String email = claims.getEmail();
            String recovery = verifiedGoogleEmail(claims.getEmailVerified(), email, claims.getClaimAsString("hd"));
            return new SocialLoginSession.Identity(provider, subject, claims.getFullName(),
                    label(email, claims.getFullName()), recovery);
        }
        if ("microsoft".equals(provider)) {
            var microsoft = token.getPrincipal();
            String name = microsoft.getAttribute("displayName");
            String email = label(microsoft.getAttribute("mail"), microsoft.getAttribute("userPrincipalName"));
            return new SocialLoginSession.Identity(provider, subject, name, label(email, name), null);
        }
        throw new DomainException(ErrorCode.INVALID_CREDENTIALS);
    }

    private static String verifiedGoogleEmail(Boolean verified, String email, String hostedDomain) {
        if (!Boolean.TRUE.equals(verified) || email == null || email.length() > EmailAddress.MAX_LENGTH
                || !email.matches(EmailAddress.PATTERN)) {
            return null;
        }
        // Google xác thực quyền sở hữu Gmail và tài khoản thuộc Google Workspace.
        return email.toLowerCase(Locale.ROOT).endsWith("@gmail.com") || (hostedDomain != null && !hostedDomain.isBlank())
                ? email : null;
    }

    private static String label(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private static String failurePage(AuthenticatedUser localUser) {
        return localUser == null ? "/login?social=failed" : "/account?social=failed";
    }

    private static void redirect(HttpServletResponse response, String path) {
        response.setStatus(HttpServletResponse.SC_SEE_OTHER);
        response.setHeader(HttpHeaders.LOCATION, path);
        response.setHeader("Referrer-Policy", "no-referrer");
    }
}
