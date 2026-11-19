package com.ptit.parking.manager.application.usecase;

import com.ptit.parking.manager.application.port.out.OwnerAccountDirectory;
import com.ptit.parking.manager.application.port.out.StudentDirectory;
import com.ptit.parking.manager.application.port.out.StudentDirectory.StudentAccount;
import com.ptit.parking.payment.application.IdempotencyGuard;
import com.ptit.parking.payment.application.IdempotencyGuard.Execution;
import com.ptit.parking.payment.application.RequestFingerprint;
import com.ptit.parking.payment.application.port.out.PointTransactionRepository;
import com.ptit.parking.payment.application.port.out.WalletRepository;
import com.ptit.parking.payment.domain.PointAdjustment;
import com.ptit.parking.payment.domain.TransactionType;
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * Đợt 3 — quản lý ghi GIAO DỊCH ĐIỀU CHỈNH giữa ví chủ bãi xe và ví một sinh viên, thay vì sửa giao dịch cũ (R10):
 * <ul>
 *   <li>{@link Direction#HOAN_DIEM}: chủ bãi → sinh viên (trừ phí nhầm, trừ hai lần, bồi thường do chủ bãi quyết định).</li>
 *   <li>{@link Direction#THU_HOI}: sinh viên → chủ bãi (lỡ hoàn nhầm cho sinh viên).</li>
 * </ul>
 * Bắt buộc ghi lý do; có thể trỏ tới giao dịch gốc. Khóa HAI ví theo id tăng dần (chống deadlock như thanh toán).
 * Bọc trong {@link IdempotencyGuard}: bấm xác nhận hai lần cũng chỉ chuyển một lần.
 */
public class AdjustPointsUseCase {

    public enum Direction { HOAN_DIEM, THU_HOI }

    public record Command(long managerId, UUID idempotencyKey, String studentCode, Direction direction, long points,
                          String reason, Long originalTransactionId) {}

    public record Result(PointAdjustment adjustment, String studentCode, String studentName, boolean replayed) {}

    private final StudentDirectory students;
    private final OwnerAccountDirectory owners;
    private final WalletRepository wallets;
    private final PointTransactionRepository ledger;
    private final IdempotencyGuard idempotency;
    private final Clock clock;

    public AdjustPointsUseCase(StudentDirectory students, OwnerAccountDirectory owners, WalletRepository wallets,
                               PointTransactionRepository ledger, IdempotencyGuard idempotency, Clock clock) {
        this.students = students;
        this.owners = owners;
        this.wallets = wallets;
        this.ledger = ledger;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    public Result adjust(Command command) {
        StudentAccount student = students.findByStudentCode(StudentDirectory.normalize(command.studentCode()))
                .orElseThrow(() -> new DomainException(ErrorCode.STUDENT_NOT_FOUND));
        if (command.points() <= 0) {
            throw new DomainException(ErrorCode.INVALID_AMOUNT);
        }
        if (command.originalTransactionId() != null && ledger.findById(command.originalTransactionId()).isEmpty()) {
            throw new DomainException(ErrorCode.TRANSACTION_NOT_FOUND);
        }
        long ownerWalletId = owners.ownerAccount().walletId();
        boolean refund = command.direction() == Direction.HOAN_DIEM;
        long sourceId = refund ? ownerWalletId : student.walletId();
        long targetId = refund ? student.walletId() : ownerWalletId;
        String hash = RequestFingerprint.of(TransactionType.DIEU_CHINH, command.managerId(), student.studentCode(),
                command.direction(), command.points(), command.reason(), command.originalTransactionId());

        Execution<PointAdjustment> execution = idempotency.execute(command.idempotencyKey(), hash, PointAdjustment.class,
                () -> {
                    Map<Long, Wallet> locked = wallets.lockInIdOrder(java.util.List.of(sourceId, targetId));
                    Wallet source = locked.get(sourceId);
                    Wallet target = locked.get(targetId);
                    PointAdjustment adjustment = PointAdjustment.execute(source, target, command.points(),
                            command.reason(), command.originalTransactionId(), command.managerId(),
                            command.idempotencyKey(), clock.instant().truncatedTo(ChronoUnit.MICROS));
                    wallets.save(source);
                    wallets.save(target);
                    return adjustment;
                });
        return new Result(execution.transaction(), student.studentCode(), student.fullName(), execution.replayed());
    }
}
