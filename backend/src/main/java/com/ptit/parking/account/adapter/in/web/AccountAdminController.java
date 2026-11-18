package com.ptit.parking.account.adapter.in.web;

import com.ptit.parking.account.application.port.out.AuditLogQuery.AuditRecord;
import com.ptit.parking.account.application.usecase.IssueTemporaryPasswordUseCase;
import com.ptit.parking.account.application.usecase.IssueTemporaryPasswordUseCase.TemporaryPassword;
import com.ptit.parking.account.application.usecase.ListAuditLogUseCase;
import com.ptit.parking.account.application.usecase.UpdateGuardProfileUseCase;
import com.ptit.parking.account.domain.GuardProfile;
import com.ptit.parking.account.domain.PhoneNumber;
import com.ptit.parking.shared.security.SessionUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quản lý tài khoản (quyền ACCOUNT_MANAGE): cấp mật khẩu tạm cho sinh viên / bảo vệ, sửa thông tin bảo vệ,
 * xem nhật ký thao tác. Người thực hiện luôn là quản lý trong phiên.
 */
@RestController
@RequestMapping("/api/v1/manager")
class AccountAdminController {

    record TemporaryPasswordResponse(long userId, String username, String fullName, String temporaryPassword) {

        static TemporaryPasswordResponse from(TemporaryPassword issued) {
            return new TemporaryPasswordResponse(issued.userId(), issued.username(), issued.fullName(),
                    issued.password());
        }
    }

    record UpdateGuardRequest(
            @NotBlank(message = "Vui lòng nhập họ tên")
            @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
            String fullName,

            @NotBlank(message = "Vui lòng nhập số điện thoại")
            @Pattern(regexp = PhoneNumber.PATTERN, message = "Số điện thoại gồm 10–11 chữ số, bắt đầu bằng 0")
            String phone) {

        UpdateGuardRequest {
            phone = phone == null ? null : phone.replaceAll("[\\s.\\-]", "");
        }
    }

    record GuardProfileResponse(long id, String username, String fullName, String phone) {

        static GuardProfileResponse from(GuardProfile guard) {
            return new GuardProfileResponse(guard.userId(), guard.username(), guard.fullName(),
                    guard.phone().orElseThrow().value());
        }
    }

    record AuditItem(long id, long actorId, String actorName, String action, Long targetUserId, String targetName,
                     String detail, String createdAt) {

        static AuditItem of(AuditRecord record, ZoneId zone) {
            return new AuditItem(record.id(), record.actorId(), record.actorName(), record.action().name(),
                    record.targetUserId(), record.targetName(), record.detail(),
                    OffsetDateTime.ofInstant(record.at(), zone).truncatedTo(ChronoUnit.SECONDS)
                            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
    }

    record AuditPage(List<AuditItem> items, boolean hasMore, Long nextBefore) {}

    private final IssueTemporaryPasswordUseCase temporaryPasswords;
    private final UpdateGuardProfileUseCase updateGuard;
    private final ListAuditLogUseCase auditLog;
    private final ZoneId zone;

    AccountAdminController(IssueTemporaryPasswordUseCase temporaryPasswords, UpdateGuardProfileUseCase updateGuard,
                           ListAuditLogUseCase auditLog, Clock clock) {
        this.temporaryPasswords = temporaryPasswords;
        this.updateGuard = updateGuard;
        this.auditLog = auditLog;
        this.zone = clock.getZone();
    }

    /** Sinh viên quên mật khẩu, mang thẻ tới quản lý. MSSV gõ thường hay thừa khoảng trắng vẫn tìm được. */
    @PostMapping("/students/{studentCode}/password-reset")
    @ResponseStatus(HttpStatus.CREATED)
    TemporaryPasswordResponse resetStudent(@AuthenticationPrincipal SessionUser manager,
                                           @PathVariable String studentCode) {
        return TemporaryPasswordResponse.from(temporaryPasswords.forStudent(manager.id(),
                studentCode.strip().toUpperCase(Locale.ROOT)));
    }

    @PostMapping("/guards/{guardId}/password-reset")
    @ResponseStatus(HttpStatus.CREATED)
    TemporaryPasswordResponse resetGuard(@AuthenticationPrincipal SessionUser manager, @PathVariable long guardId) {
        return TemporaryPasswordResponse.from(temporaryPasswords.forGuard(manager.id(), guardId));
    }

    @PatchMapping("/guards/{guardId}")
    GuardProfileResponse updateGuard(@AuthenticationPrincipal SessionUser manager, @PathVariable long guardId,
                                     @Valid @RequestBody UpdateGuardRequest body) {
        return GuardProfileResponse.from(updateGuard.update(new UpdateGuardProfileUseCase.Command(
                manager.id(), guardId, body.fullName(), body.phone())));
    }

    @GetMapping("/audit-logs")
    AuditPage auditLogs(@RequestParam(required = false) @Positive(message = "targetUserId phải > 0") Long targetUserId,
                        @RequestParam(required = false) @Positive(message = "before phải > 0") Long before,
                        @RequestParam(defaultValue = "20")
                        @Min(value = 1, message = "limit từ 1 đến 50")
                        @Max(value = ListAuditLogUseCase.MAX_LIMIT, message = "limit từ 1 đến 50") int limit) {
        ListAuditLogUseCase.Page page = auditLog.page(targetUserId, before, limit);
        return new AuditPage(page.items().stream().map(item -> AuditItem.of(item, zone)).toList(),
                page.hasMore(), page.nextBefore());
    }
}
