package com.ptit.parking.manager.application.usecase;

import com.ptit.parking.auth.domain.AccountStatus;
import com.ptit.parking.auth.domain.Guard;
import com.ptit.parking.manager.application.port.out.GuardAccountRepository;
import com.ptit.parking.shared.application.port.out.AuditTrail;
import com.ptit.parking.shared.application.port.out.AuditTrail.AuditAction;
import com.ptit.parking.shared.application.port.out.AuditTrail.AuditEntry;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Clock;

/**
 * QL04 (Đặc tả API #22) — quản lý khóa / mở khóa tài khoản bảo vệ.
 * <ul>
 *   <li>id không phải bảo vệ → GUARD_NOT_FOUND: API này không khóa được quản lý, sinh viên hay chủ tài khoản.</li>
 *   <li>Đã ở đúng trạng thái → giữ nguyên, vẫn thành công (idempotent).</li>
 *   <li>Không có xóa (R14): lượt gửi cũ còn tham chiếu tới bảo vệ.</li>
 * </ul>
 * Phiên đang mở của bảo vệ bị khóa không cần hủy ở đây: ActiveAccountFilter đọc trạng thái ở MỌI request,
 * nên request kế tiếp của bảo vệ đó nhận ACCOUNT_LOCKED và phiên bị hủy.
 * <p>Đợt 4a: mỗi lần trạng thái THỰC SỰ đổi thì ghi nhật ký KHOA_TAI_KHOAN / MO_KHOA_TAI_KHOAN; bấm lại khi đã đúng
 * trạng thái thì không ghi gì.
 */
public class ChangeGuardStatusUseCase {

    public record Command(long managerId, long guardId, AccountStatus status) {}

    private final GuardAccountRepository guards;
    private final TransactionRunner transactions;
    private final AuditTrail audit;
    private final Clock clock;

    public ChangeGuardStatusUseCase(GuardAccountRepository guards, TransactionRunner transactions, AuditTrail audit,
                                    Clock clock) {
        this.guards = guards;
        this.transactions = transactions;
        this.audit = audit;
        this.clock = clock;
    }

    public Guard change(Command command) {
        return transactions.inTransaction(() -> {
            Guard guard = guards.findById(command.guardId())
                    .orElseThrow(() -> new DomainException(ErrorCode.GUARD_NOT_FOUND));
            if (guard.status() == command.status()) {
                return guard;                                   // bấm lại: không ghi gì, vẫn 200
            }
            AuditAction action = switch (command.status()) {
                case BI_KHOA -> {
                    guard.lock();
                    yield AuditAction.KHOA_TAI_KHOAN;
                }
                case HOAT_DONG -> {
                    guard.unlock();
                    yield AuditAction.MO_KHOA_TAI_KHOAN;
                }
            };
            guards.saveStatus(guard);
            String verb = action == AuditAction.KHOA_TAI_KHOAN ? "Khóa" : "Mở khóa";
            audit.record(new AuditEntry(command.managerId(), action, guard.id(),
                    verb + " tài khoản bảo vệ " + guard.username(), clock.instant()));
            return guard;
        });
    }
}
