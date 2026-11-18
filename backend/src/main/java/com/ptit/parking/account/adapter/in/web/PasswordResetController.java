package com.ptit.parking.account.adapter.in.web;

import com.ptit.parking.account.application.usecase.ConfirmPasswordResetUseCase;
import com.ptit.parking.account.application.usecase.RequestPasswordResetUseCase;
import com.ptit.parking.account.domain.EmailAddress;
import com.ptit.parking.account.domain.PasswordPolicy;
import com.ptit.parking.shared.ratelimit.RateLimiter;
import com.ptit.parking.shared.ratelimit.TooManyRequestsException;
import com.ptit.parking.shared.security.SecurityConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quên mật khẩu (chưa đăng nhập, không cần CSRF — xem SecurityConfig). Mỗi IP tối đa 10 lần / 15 phút cho
 * cả hai bước, để không ai dùng form này gửi email hàng loạt hay dò mã.
 */
@RestController
class PasswordResetController {

    static final int LIMIT_PER_IP = 10;

    record ResetRequest(
            @NotBlank(message = "Vui lòng nhập email")
            @Size(max = EmailAddress.MAX_LENGTH, message = "Email tối đa 100 ký tự")
            @Pattern(regexp = EmailAddress.PATTERN, message = "Email không đúng định dạng")
            String email) {
    }

    record ConfirmRequest(
            @NotBlank(message = "Vui lòng nhập email")
            @Pattern(regexp = EmailAddress.PATTERN, message = "Email không đúng định dạng")
            String email,

            @NotBlank(message = "Vui lòng nhập mã xác nhận")
            @Pattern(regexp = "^[0-9]{6}$", message = "Mã xác nhận gồm 6 chữ số")
            String code,

            @NotBlank(message = "Vui lòng nhập mật khẩu mới")
            @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH, message = "Mật khẩu 8–72 ký tự")
            String newPassword) {
    }

    /** Luôn cùng một câu trả lời, dù email có tồn tại hay không. */
    record ResetAccepted(String message) {}

    private final RequestPasswordResetUseCase requestReset;
    private final ConfirmPasswordResetUseCase confirmReset;
    private final RateLimiter perIp;

    PasswordResetController(RequestPasswordResetUseCase requestReset, ConfirmPasswordResetUseCase confirmReset,
                            Clock clock) {
        this.requestReset = requestReset;
        this.confirmReset = confirmReset;
        this.perIp = new RateLimiter(LIMIT_PER_IP, Duration.ofMinutes(15), clock);
    }

    @PostMapping(SecurityConfig.PASSWORD_RESET_URL)
    @ResponseStatus(HttpStatus.ACCEPTED)
    ResetAccepted request(@Valid @RequestBody ResetRequest body, HttpServletRequest http) {
        throttle(http);
        requestReset.request(body.email());
        return new ResetAccepted("Nếu email đã được đăng ký, mã xác nhận đã được gửi tới email đó. "
                + "Mã có hiệu lực 10 phút.");
    }

    @PostMapping(SecurityConfig.PASSWORD_RESET_CONFIRM_URL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirm(@Valid @RequestBody ConfirmRequest body, HttpServletRequest http) {
        throttle(http);
        confirmReset.confirm(new ConfirmPasswordResetUseCase.Command(body.email(), body.code(), body.newPassword()));
    }

    private void throttle(HttpServletRequest http) {
        perIp.tryAcquire(http.getRemoteAddr()).ifPresent(wait -> {
            throw new TooManyRequestsException(wait);
        });
    }
}
