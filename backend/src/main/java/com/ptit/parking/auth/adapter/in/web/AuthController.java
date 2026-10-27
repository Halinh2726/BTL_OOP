package com.ptit.parking.auth.adapter.in.web;

import com.ptit.parking.auth.application.AuthenticatedUser;
import com.ptit.parking.auth.application.usecase.GetCurrentUserUseCase;
import com.ptit.parking.auth.application.usecase.LoginUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C01 — API #1 đăng nhập, #3 thông tin phiên. (#2 đăng xuất do Spring Security xử lý, xem SecurityConfig.)
 * Controller MỎNG: nhận request -> gọi use case -> đổi kết quả sang response. Không chứa nghiệp vụ.
 */
@RestController
@RequestMapping("/api/v1")
class AuthController {

    private final LoginUseCase loginUseCase;
    private final GetCurrentUserUseCase getCurrentUser;
    private final SessionSignIn sessionSignIn;
    private final LoginThrottle loginThrottle;

    AuthController(LoginUseCase loginUseCase, GetCurrentUserUseCase getCurrentUser, SessionSignIn sessionSignIn,
                   LoginThrottle loginThrottle) {
        this.loginUseCase = loginUseCase;
        this.getCurrentUser = getCurrentUser;
        this.sessionSignIn = sessionSignIn;
        this.loginThrottle = loginThrottle;
    }

    @PostMapping("/auth/login")
    MeResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        String throttleKey = LoginThrottle.keyOf(request, body.username());
        loginThrottle.beforeAttempt(throttleKey);                      // sai 5 lần trong 1 phút -> 429
        AuthenticatedUser user = loginUseCase.login(body.username(), body.password());
        loginThrottle.succeeded(throttleKey);
        sessionSignIn.signIn(user, request, response);
        return MeResponse.from(user);
    }

    /** Dùng để khôi phục phiên khi tải lại trang. Đọc lại từ CSDL để bắt kịp trạng thái bị khóa. */
    @GetMapping("/me")
    MeResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return MeResponse.from(getCurrentUser.get(principal.id()));
    }
}