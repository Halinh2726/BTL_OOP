package com.ptit.parking.gate.domain;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * Ca trực của một bảo vệ, kiêm biên bản bàn giao ca và dữ liệu chấm công. Vòng đời chỉ đi MỘT chiều, mỗi bước là
 * một phương thức (đóng gói — không có setter):
 * <pre>
 *   start(tiền nhận ca) ──▶ ĐANG TRỰC ──end(số liệu, tiền bàn giao, ghi chú)──▶ CHỜ NHẬN ──receive(bảo vệ ca sau)──▶ ĐÃ BÀN GIAO
 * </pre>
 * Quy tắc nằm trong đối tượng: không kết thúc ca hai lần, không nhận biên bản của chính mình, không nhận lại
 * biên bản đã có người nhận. Khớp các ràng buộc ck_guard_shifts_* của CSDL.
 * <p>Tiền mặt (V013): {@code openingCash} bảo vệ khai lúc nhận ca; {@code expectedOpeningCash} là tổng tiền các ca
 * trước đã bàn giao — lệch nhau thì {@link #openingDiscrepancy()} khác 0 để quản lý đối chiếu.
 */
public final class GuardShift {

    public static final int MAX_NOTE = 500;

    private final Long id;
    private final long guardId;
    private final Instant startedAt;
    private final CashOnHand openingCash;
    private final CashOnHand expectedOpeningCash;      // null: không nhận biên bản nào khi vào ca
    private Instant endedAt;
    private ShiftSummary summary;
    private CashOnHand closingCash;
    private String handoverNote;
    private Long receivedBy;
    private Instant receivedAt;

    private GuardShift(Long id, long guardId, Instant startedAt, CashOnHand openingCash, CashOnHand expectedOpeningCash,
                       Instant endedAt, ShiftSummary summary, CashOnHand closingCash, String handoverNote,
                       Long receivedBy, Instant receivedAt) {
        this.id = id;
        this.guardId = guardId;
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.openingCash = Objects.requireNonNull(openingCash, "openingCash");
        this.expectedOpeningCash = expectedOpeningCash;
        this.endedAt = endedAt;
        this.summary = summary;
        this.closingCash = closingCash;
        this.handoverNote = handoverNote;
        this.receivedBy = receivedBy;
        this.receivedAt = receivedAt;
        if ((endedAt == null) != (summary == null) || (endedAt == null) != (closingCash == null)) {
            throw new IllegalArgumentException("Ca đã kết thúc phải có số liệu và tiền bàn giao, ca đang trực thì chưa");
        }
    }

    /**
     * Nhận ca.
     *
     * @param expectedOpeningCash tổng tiền các ca trước bàn giao lại; {@code null} nếu không nhận biên bản nào
     */
    public static GuardShift start(long guardId, CashOnHand openingCash, CashOnHand expectedOpeningCash, Instant now) {
        return new GuardShift(null, guardId, now.truncatedTo(ChronoUnit.MICROS), openingCash, expectedOpeningCash,
                null, null, null, null, null, null);
    }

    public static GuardShift restore(long id, long guardId, Instant startedAt, CashOnHand openingCash,
                                     CashOnHand expectedOpeningCash, Instant endedAt, ShiftSummary summary,
                                     CashOnHand closingCash, String handoverNote, Long receivedBy, Instant receivedAt) {
        return new GuardShift(id, guardId, startedAt, openingCash, expectedOpeningCash, endedAt, summary, closingCash,
                handoverNote, receivedBy, receivedAt);
    }

    /** Kết thúc ca: ghi số liệu bàn giao (hệ thống đếm) và số tiền mặt bàn giao (bảo vệ khai). */
    public void end(ShiftSummary shiftSummary, CashOnHand handedOverCash, String note, Instant now) {
        if (!isOpen()) {
            throw new DomainException(ErrorCode.NO_OPEN_SHIFT);
        }
        String trimmed = note == null ? "" : note.strip();
        if (trimmed.length() > MAX_NOTE) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR);
        }
        this.summary = Objects.requireNonNull(shiftSummary, "shiftSummary");
        this.closingCash = Objects.requireNonNull(handedOverCash, "handedOverCash");
        this.handoverNote = trimmed.isEmpty() ? null : trimmed;
        this.endedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    /** Bảo vệ ca sau xác nhận đã đọc biên bản và nhận ca. */
    public void receive(long receiverGuardId, Instant now) {
        if (!isAwaitingReceipt() || receiverGuardId == guardId) {
            throw new DomainException(ErrorCode.HANDOVER_PENDING);
        }
        this.receivedBy = receiverGuardId;
        this.receivedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    /** Bản sao đã có id do CSDL sinh. */
    public GuardShift withId(long newId) {
        return new GuardShift(newId, guardId, startedAt, openingCash, expectedOpeningCash, endedAt, summary,
                closingCash, handoverNote, receivedBy, receivedAt);
    }

    /** Thời gian trực — chấm công. Ca đang trực tính tới {@code now}. */
    public Duration worked(Instant now) {
        Instant end = endedAt != null ? endedAt : now;
        return end.isBefore(startedAt) ? Duration.ZERO : Duration.between(startedAt, end);
    }

    /** Tiền khai nhận ca trừ tiền các ca trước bàn giao; rỗng khi không nhận biên bản nào. */
    public Optional<Long> openingDiscrepancy() {
        return Optional.ofNullable(expectedOpeningCash).map(openingCash::minus);
    }

    public boolean isOpen() {
        return endedAt == null;
    }

    public boolean isAwaitingReceipt() {
        return !isOpen() && receivedBy == null;
    }

    public Long id() { return id; }

    public long guardId() { return guardId; }

    public Instant startedAt() { return startedAt; }

    public CashOnHand openingCash() { return openingCash; }

    public CashOnHand expectedOpeningCash() { return expectedOpeningCash; }

    public Instant endedAt() { return endedAt; }

    public ShiftSummary summary() { return summary; }

    public CashOnHand closingCash() { return closingCash; }

    public String handoverNote() { return handoverNote; }

    public Long receivedBy() { return receivedBy; }

    public Instant receivedAt() { return receivedAt; }
}
