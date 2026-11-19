package com.ptit.parking.report.application.usecase;

import com.ptit.parking.report.application.port.out.CashPositionQuery;
import com.ptit.parking.report.application.port.out.ReconciliationRepository;
import com.ptit.parking.report.application.port.out.ReconciliationRepository.Closed;
import com.ptit.parking.report.domain.CashPosition;
import com.ptit.parking.report.domain.DailyReconciliation;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Đợt 4a — đối soát tiền mặt cuối ngày. Quản lý xem sổ sách của ngày (tiền vào, tiền ra, tiền phải có — R13; điểm
 * có bảo toàn không — R12), đếm tiền thực tế rồi CHỐT: tạo biên bản không sửa được. Lệch thì bắt buộc giải trình.
 */
public class ReconciliationUseCase {

    public static final int HISTORY_LIMIT = 30;

    /** Sổ sách của một ngày, kèm biên bản nếu ngày đó đã chốt. */
    public record Day(CashPosition position, Optional<Closed> closed) {}

    public record Command(long managerId, LocalDate date, long countedCash, String note) {}

    private final CashPositionQuery positions;
    private final ReconciliationRepository reconciliations;
    private final TransactionRunner transactions;
    private final Clock clock;

    public ReconciliationUseCase(CashPositionQuery positions, ReconciliationRepository reconciliations,
                                 TransactionRunner transactions, Clock clock) {
        this.positions = positions;
        this.reconciliations = reconciliations;
        this.transactions = transactions;
        this.clock = clock;
    }

    public Day day(LocalDate date) {
        requireNotFuture(date);
        return new Day(positions.positionOn(date, clock.getZone()), reconciliations.findByDate(date));
    }

    public Closed close(Command command) {
        requireNotFuture(command.date());
        return transactions.inTransaction(() -> {
            if (reconciliations.findByDate(command.date()).isPresent()) {
                throw new DomainException(ErrorCode.ALREADY_RECONCILED);
            }
            CashPosition position = positions.positionOn(command.date(), clock.getZone());
            DailyReconciliation closed = DailyReconciliation.close(position, command.countedCash(), command.note(),
                    command.managerId(), clock.instant());
            // Hai quản lý chốt cùng ngày cùng lúc: UNIQUE uq_daily_reconciliations_date chặn người sau
            reconciliations.save(closed);
            return reconciliations.findByDate(command.date()).orElseThrow();
        });
    }

    public List<Closed> history() {
        return reconciliations.recent(HISTORY_LIMIT);
    }

    private void requireNotFuture(LocalDate date) {
        if (date == null || date.isAfter(LocalDate.now(clock))) {
            throw new DomainException(ErrorCode.RECONCILIATION_DATE_INVALID);
        }
    }
}
