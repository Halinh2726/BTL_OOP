package com.ptit.parking.manager.application.usecase;

import com.ptit.parking.auth.domain.AccountStatus;
import com.ptit.parking.auth.domain.Guard;
import com.ptit.parking.manager.application.port.out.GuardAccountRepository;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;

/**
 * QL04 (Đặc tả API #22) — quản lý khóa / mở khóa tài khoản bảo vệ.
 * <ul>
 *   <li>id không phải bảo vệ → GUARD_NOT_FOUND: API này không khóa được quản lý, sinh viên hay chủ tài khoản.</li>
 *   <li>Đã ở đúng trạng thái → giữ nguyên, vẫn thành công (idempotent).</li>
 *   <li>Không có xóa (R14): lượt gửi cũ còn tham chiếu tới bảo vệ.</li>
 * </ul>
 * Phiên đang mở của bảo vệ bị khóa không cần hủy ở đây: ActiveAccountFilter đọc trạng thái ở MỌI request,
 * nên request kế tiếp của bảo vệ đó nhận ACCOUNT_LOCKED và phiên bị hủy.
 */
public class ChangeGuardStatusUseCase {

    public record Command(long guardId, AccountStatus status) {}

    private final GuardAccountRepository guards;
    private final TransactionRunner transactions;

    public ChangeGuardStatusUseCase(GuardAccountRepository guards, TransactionRunner transactions) {
        this.guards = guards;
        this.transactions = transactions;
    }

    public Guard change(Command command) {
        return transactions.inTransaction(() -> {
            Guard guard = guards.findById(command.guardId())
                    .orElseThrow(() -> new DomainException(ErrorCode.GUARD_NOT_FOUND));
            if (guard.status() == command.status()) {
                return guard;                                   // bấm lại: không ghi gì, vẫn 200
            }
            switch (command.status()) {
                case BI_KHOA -> guard.lock();
                case HOAT_DONG -> guard.unlock();
            }
            guards.saveStatus(guard);
            return guard;
        });
    }
}
