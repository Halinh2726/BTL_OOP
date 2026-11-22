package com.ptit.parking.account.adapter.in.web;

import com.ptit.parking.account.application.port.out.AccountProfileRepository;
import com.ptit.parking.account.application.port.out.AuditLogQuery;
import com.ptit.parking.account.application.port.out.AuditLogQuery.AuditRecord;
import com.ptit.parking.account.domain.AccountProfile;
import com.ptit.parking.account.domain.EmailAddress;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.shared.security.CredentialStateLookup;
import com.ptit.parking.shared.security.CredentialStateLookup.CredentialState;
import com.ptit.parking.shared.security.SessionUser;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trạng thái bảo mật & căn cứ xác thực mật khẩu của chính tài khoản đang đăng nhập.
 * Chỉ cần đã đăng nhập (mọi vai trò đều xem được trạng thái & nhật ký thao tác trên chính tài khoản mình).
 */
@RestController
@RequestMapping("/api/v1")
class MySecurityActivityController {

    private static final int SESSION_MAX_HOURS = 12;
    private static final int RECENT_LIMIT = 15;

    record SecurityActivityItem(long id, String action, String actorName, String detail, String createdAt) {

        static SecurityActivityItem of(AuditRecord record, ZoneId zone) {
            return new SecurityActivityItem(record.id(), record.action().name(), record.actorName(),
                    record.detail(), formatTime(record.at(), zone));
        }
    }

    record SecurityActivityResponse(
            boolean hasEmail,
            String maskedEmail,
            boolean mustChangePassword,
            String passwordChangedAt,
            int sessionMaxHours,
            String changePasswordBasis,
            String forgotPasswordBasis,
            List<SecurityActivityItem> recentActivities) {}

    private final AccountProfileRepository profiles;
    private final CredentialStateLookup credentialStates;
    private final AuditLogQuery auditLogs;
    private final ZoneId zone;

    MySecurityActivityController(AccountProfileRepository profiles,
                                 CredentialStateLookup credentialStates,
                                 AuditLogQuery auditLogs,
                                 Clock clock) {
        this.profiles = profiles;
        this.credentialStates = credentialStates;
        this.auditLogs = auditLogs;
        this.zone = clock.getZone();
    }

    @GetMapping("/me/security-activity")
    SecurityActivityResponse summary(@AuthenticationPrincipal SessionUser user) {
        AccountProfile profile = profiles.findById(user.id())
                .orElseThrow(() -> new DomainException(ErrorCode.UNAUTHENTICATED));
        CredentialState credential = credentialStates.stateOf(user.id());
        String maskedEmail = profile.email().map(EmailAddress::masked).orElse(null);
        List<SecurityActivityItem> recent = auditLogs.page(user.id(), null, RECENT_LIMIT)
                .stream()
                .map(record -> SecurityActivityItem.of(record, zone))
                .toList();

        return new SecurityActivityResponse(
                profile.email().isPresent(),
                maskedEmail,
                credential.mustChangePassword(),
                credential.passwordChangedAt() == null ? null : formatTime(credential.passwordChangedAt(), zone),
                SESSION_MAX_HOURS,
                "Đổi mật khẩu trực tiếp xác thực dựa trên mật khẩu hiện tại (đối chiếu BCrypt). "
                        + "Mật khẩu mới phải từ 8–72 ký tự, không trùng mật khẩu đang dùng; "
                        + "sau khi đổi sẽ thu hồi toàn bộ phiên đăng nhập ở thiết bị khác và gửi thông báo bảo mật.",
                "Quên mật khẩu xác thực dựa trên một trong hai kênh: (1) nhập Email đã đăng ký, Tên đăng nhập hoặc MSSV "
                        + "để nhận mã OTP 6 chữ số qua email (hiệu lực 10 phút, chống gửi liên tục và giới hạn thử sai); "
                        + "hoặc (2) liên hệ Quản lý cấp mật khẩu tạm (bắt buộc đổi lại ngay khi đăng nhập).",
                recent);
    }

    private static String formatTime(Instant instant, ZoneId zone) {
        return OffsetDateTime.ofInstant(instant, zone).truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
