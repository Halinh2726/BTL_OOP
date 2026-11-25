package com.ptit.parking.account.presentation.web;

import com.ptit.parking.account.application.usecase.ChangePasswordUseCase;
import com.ptit.parking.account.application.usecase.ManageMyProfileUseCase;
import com.ptit.parking.account.domain.EmailAddress;
import com.ptit.parking.account.domain.PasswordPolicy;
import com.ptit.parking.account.domain.PhoneNumber;
import com.ptit.parking.shared.infrastructure.security.CredentialFreshnessFilter;
import com.ptit.parking.shared.infrastructure.security.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Tài khoản của tôi" — mọi vai trò đã đăng nhập (không cần mã quyền riêng): xem/sửa thông tin cá nhân, đổi mật khẩu.
 * Người bị thao tác LUÔN là người trong phiên; không có tham số userId.
 */
@RestController
@RequestMapping("/api/v1/me")
class MyAccountController {

    /** Email, số điện thoại để trống = xóa. Không có trường username / studentCode: hai trường này không sửa được. */
    record UpdateProfileRequest(
            @NotBlank(message = "Vui lòng nhập họ tên")
            @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
            String fullName,

            @Size(max = EmailAddress.MAX_LENGTH, message = "Email tối đa 100 ký tự")
            @Pattern(regexp = "^$|" + EmailAddress.PATTERN, message = "Email không đúng định dạng")
            String email,

            @Pattern(regexp = "^$|" + PhoneNumber.PATTERN, message = "Số điện thoại gồm 10–11 chữ số, bắt đầu bằng 0")
            String phone) {

        UpdateProfileRequest {
            email = email == null ? null : email.strip();
            phone = phone == null ? null : phone.replaceAll("[\\s.\\-]", "");
        }
    }

    record ChangePasswordRequest(
            @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại")
            String currentPassword,

            @NotBlank(message = "Vui lòng nhập mật khẩu mới")
            @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH, message = "Mật khẩu 8–72 ký tự")
            String newPassword) {
    }

    private final ManageMyProfileUseCase profiles;
    private final ChangePasswordUseCase changePassword;

    MyAccountController(ManageMyProfileUseCase profiles, ChangePasswordUseCase changePassword) {
        this.profiles = profiles;
        this.changePassword = changePassword;
    }

    @GetMapping("/profile")
    ProfileResponse profile(@AuthenticationPrincipal SessionUser user) {
        return ProfileResponse.from(profiles.get(user.id()));
    }

    @PatchMapping("/profile")
    ProfileResponse update(@AuthenticationPrincipal SessionUser user, @Valid @RequestBody UpdateProfileRequest body) {
        return ProfileResponse.from(profiles.update(new ManageMyProfileUseCase.UpdateCommand(
                user.id(), body.fullName(), body.email(), body.phone())));
    }

    /**
     * Đổi mật khẩu. Phiên HIỆN TẠI được đổi mã phiên mới và ghi mốc xác minh = thời điểm đổi, nên vẫn dùng tiếp;
     * các phiên khác của tài khoản này bị đăng xuất ở request kế tiếp.
     */
    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@AuthenticationPrincipal SessionUser user, @Valid @RequestBody ChangePasswordRequest body,
                        HttpServletRequest request) {
        Instant changedAt = changePassword.change(new ChangePasswordUseCase.Command(
                user.id(), body.currentPassword(), body.newPassword()));
        request.changeSessionId();
        request.getSession().setAttribute(CredentialFreshnessFilter.CREDENTIALS_VERIFIED_AT, changedAt);
    }
}
