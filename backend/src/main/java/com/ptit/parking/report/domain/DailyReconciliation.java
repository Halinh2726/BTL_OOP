package com.ptit.parking.report.domain;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Biên bản đối soát tiền mặt cuối ngày ("chốt sổ"). Bất biến sau khi tạo — không có setter, CSDL cũng chặn
 * UPDATE/DELETE (cùng tinh thần R10): sai thì ghi bù ở ngày sau, không sửa biên bản cũ.
 * <p>Quy tắc nằm TRONG đối tượng (đóng gói): chỉ tạo được qua {@link #close} — tiền đếm được không âm, lệch so với
 * sổ sách thì bắt buộc có giải trình.
 */
public final class DailyReconciliation {

    public static final int MAX_NOTE = 500;
    public static final long MAX_CASH = 1_000_000_000_000L;

    private final Long id;
    private final LocalDate businessDate;
    private final long cashIn;
    private final long cashOut;
    private final long expectedCash;
    private final long countedCash;
    private final boolean pointsBalanced;
    private final String note;
    private final long closedBy;
    private final Instant closedAt;

    private DailyReconciliation(Long id, LocalDate businessDate, long cashIn, long cashOut, long expectedCash,
                                long countedCash, boolean pointsBalanced, String note, long closedBy, Instant closedAt) {
        this.id = id;
        this.businessDate = Objects.requireNonNull(businessDate, "businessDate");
        this.cashIn = cashIn;
        this.cashOut = cashOut;
        this.expectedCash = expectedCash;
        this.countedCash = countedCash;
        this.pointsBalanced = pointsBalanced;
        this.note = note;
        this.closedBy = closedBy;
        this.closedAt = Objects.requireNonNull(closedAt, "closedAt");
    }

    /**
     * Quản lý chốt sổ một ngày: so số tiền đếm được với tình hình sổ sách {@code position}.
     *
     * @throws DomainException INVALID_AMOUNT nếu tiền đếm được âm hoặc quá lớn; VALIDATION_ERROR nếu giải trình quá
     *                         dài; RECONCILIATION_NOTE_REQUIRED nếu lệch mà không giải trình
     */
    public static DailyReconciliation close(CashPosition position, long countedCash, String note, long managerId,
                                            Instant now) {
        Objects.requireNonNull(position, "position");
        if (countedCash < 0 || countedCash > MAX_CASH) {
            throw new DomainException(ErrorCode.INVALID_AMOUNT);
        }
        String text = note == null || note.isBlank() ? null : note.strip();
        if (text != null && text.length() > MAX_NOTE) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR);
        }
        if (countedCash != position.expectedCash() && text == null) {
            throw new DomainException(ErrorCode.RECONCILIATION_NOTE_REQUIRED);
        }
        return new DailyReconciliation(null, position.date(), position.cashIn(), position.cashOut(),
                position.expectedCash(), countedCash, position.pointsBalanced(), text, managerId, now);
    }

    /** Dựng lại từ CSDL (adapter). */
    public static DailyReconciliation restore(long id, LocalDate businessDate, long cashIn, long cashOut,
                                              long expectedCash, long countedCash, boolean pointsBalanced, String note,
                                              long closedBy, Instant closedAt) {
        return new DailyReconciliation(id, businessDate, cashIn, cashOut, expectedCash, countedCash, pointsBalanced,
                note, closedBy, closedAt);
    }

    public DailyReconciliation withId(long newId) {
        return new DailyReconciliation(newId, businessDate, cashIn, cashOut, expectedCash, countedCash, pointsBalanced,
                note, closedBy, closedAt);
    }

    /** Dương: thừa tiền; âm: thiếu tiền so với sổ sách. */
    public long discrepancy() {
        return countedCash - expectedCash;
    }

    public ReconciliationOutcome outcome() {
        return ReconciliationOutcome.of(discrepancy());
    }

    public Long id() { return id; }

    public LocalDate businessDate() { return businessDate; }

    public long cashIn() { return cashIn; }

    public long cashOut() { return cashOut; }

    public long expectedCash() { return expectedCash; }

    public long countedCash() { return countedCash; }

    public boolean pointsBalanced() { return pointsBalanced; }

    public String note() { return note; }

    public long closedBy() { return closedBy; }

    public Instant closedAt() { return closedAt; }
}
