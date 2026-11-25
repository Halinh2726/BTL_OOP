package com.ptit.parking.auth.presentation.web;

import com.ptit.parking.account.domain.PasswordPolicy;
import com.ptit.parking.auth.application.AuthenticatedUser;
import com.ptit.parking.auth.application.usecase.StudentSocialSignInUseCase;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(SocialLoginClients.BASE)
class SocialLoginController {

    record PendingResponse(UUID id, String provider, String fullName, String label) {}
    record StatusResponse(List<String> providers, List<String> linked, PendingResponse pending) {}
    record LinkRequest(@NotNull UUID id) {}

    record RegistrationRequest(
            @NotNull UUID id,
            @NotBlank @Pattern(regexp = "^[A-Z0-9]{1,20}$",
                    message = "MSSV chỉ gồm chữ và số, tối đa 20 ký tự") String studentCode,
            @NotBlank @Size(max = 100, message = "Họ tên tối đa 100 ký tự") String fullName,
            @NotBlank @Pattern(regexp = "^[a-z0-9._]{4,50}$",
                    message = "Tên đăng nhập 4–50 ký tự, chỉ gồm chữ thường, số, dấu chấm, gạch dưới") String username,
            @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH,
                    message = "Mật khẩu từ 8 đến 72 ký tự") String password) {

        RegistrationRequest {
            studentCode = studentCode == null ? null : studentCode.strip().toUpperCase(Locale.ROOT);
            fullName = fullName == null ? null : fullName.strip();
            password = "".equals(password) ? null : password;
        }

        @Override
        public String toString() {
            return "RegistrationRequest[id=" + id + "]";
        }
    }

    private final SocialLoginClients clients;
    private final StudentSocialSignInUseCase students;
    private final SocialLoginSession pending;
    private final SessionSignIn sessions;

    SocialLoginController(SocialLoginClients clients, StudentSocialSignInUseCase students,
                          SocialLoginSession pending, SessionSignIn sessions) {
        this.clients = clients;
        this.students = students;
        this.pending = pending;
        this.sessions = sessions;
    }

    @GetMapping("/status")
    StatusResponse status(@AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        var flow = pending.find(request, user == null ? null : user.id());
        var visible = flow == null || !clients.isActive(flow.identity().provider()) ? null : new PendingResponse(flow.id(), flow.identity().provider(),
                flow.identity().fullName(), flow.identity().label());
        return new StatusResponse(clients.providers(), students.linkedProviders(user), visible);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    MeResponse register(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody RegistrationRequest body,
                        HttpServletRequest request, HttpServletResponse response) {
        if (user != null) {
            throw new DomainException(ErrorCode.FORBIDDEN);
        }
        var identity = pending.require(request, body.id(), null).identity();
        requireActiveProvider(identity.provider());
        AuthenticatedUser registered = students.register(identity.provider(), identity.subject(),
                body.studentCode(), body.fullName(), body.username(), identity.recoveryEmail(), body.password());
        pending.clear(request, body.id(), null);
        sessions.signIn(registered, request, response);
        return MeResponse.from(registered, false);
    }

    @PostMapping("/link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void link(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody LinkRequest body,
              HttpServletRequest request) {
        var identity = pending.require(request, body.id(), user.id()).identity();
        requireActiveProvider(identity.provider());
        students.link(user, identity.provider(), identity.subject());
        pending.clear(request, body.id(), user.id());
    }

    private void requireActiveProvider(String provider) {
        if (!clients.isActive(provider)) {
            throw new DomainException(ErrorCode.SOCIAL_FLOW_EXPIRED);
        }
    }
}
