package com.ptit.parking.owner.domain;

import com.ptit.parking.payment.domain.ExchangeRate;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Yêu cầu rút điểm của chủ bãi xe (Đợt 3) — thay cho việc quản lý tự rút điểm bất kỳ lúc nào.
 * <p><b>Đóng gói:</b> chỉ đổi trạng thái qua các hành vi nghiệp vụ; mỗi hành vi tự kiểm tra:
 * <ul>
 *   <li>đúng NGƯỜI: chủ bãi xe tạo / hủy / xác nhận / khiếu nại yêu cầu CỦA MÌNH;</li>
 *   <li>tách bạch (R18): người xử lý (trả tiền, từ chối, giải quyết khiếu nại) KHÁC người tạo;</li>
 *   <li>đúng THỨ TỰ: theo máy trạng thái {@link WithdrawalStatus}.</li>
 * </ul>
 * Điểm CHỈ bị trừ khi quản lý trả tiền (use case tạo giao dịch RUT_DIEM rồi gọi {@link #markPaid}).
 * Tỷ lệ quy đổi được chốt lúc tạo yêu cầu (R11) để số tiền chủ bãi thấy lúc tạo đúng bằng số tiền được trả.
 */
public final class WithdrawalRequest {

    public static final int MAX_NOTE = 500;
    public static final long MAX_POINTS = 10_000_000L;

    private final Long id;
    private final long ownerId;
    private final long points;
    private final ExchangeRate rate;
    private final Instant createdAt;
    private WithdrawalStatus status;
    private Instant cancelledAt;
    private Long processedBy;
    private Instant processedAt;
    private String rejectReason;
    private Long transactionId;
    private Instant confirmedAt;
    private String complaintNote;
    private Instant complainedAt;
    private String resolutionNote;
    private Long resolvedBy;
    private Instant resolvedAt;

    private WithdrawalRequest(Builder b) {
        this.id = b.id;
        this.ownerId = b.ownerId;
        this.points = b.points;
        this.rate = Objects.requireNonNull(b.rate, "rate");
        this.createdAt = Objects.requireNonNull(b.createdAt, "createdAt");
        this.status = Objects.requireNonNull(b.status, "status");
        this.cancelledAt = b.cancelledAt;
        this.processedBy = b.processedBy;
        this.processedAt = b.processedAt;
        this.rejectReason = b.rejectReason;
        this.transactionId = b.transactionId;
        this.confirmedAt = b.confirmedAt;
        this.complaintNote = b.complaintNote;
        this.complainedAt = b.complainedAt;
        this.resolutionNote = b.resolutionNote;
        this.resolvedBy = b.resolvedBy;
        this.resolvedAt = b.resolvedAt;
    }

    /** Chủ bãi xe tạo yêu cầu rút {@code points} điểm, nhận tiền mặt. R15 (không vượt số điểm còn rút được) do use case kiểm tra. */
    public static WithdrawalRequest create(long ownerId, long points, ExchangeRate rate, Instant now) {
        if (points <= 0 || points > MAX_POINTS) {
            throw new DomainException(ErrorCode.INVALID_AMOUNT);
        }
        Builder b = new Builder();
        b.ownerId = ownerId;
        b.points = points;
        b.rate = rate;
        b.createdAt = now.truncatedTo(ChronoUnit.MICROS);
        b.status = WithdrawalStatus.CHO_XU_LY;
        return new WithdrawalRequest(b);
    }

    /** Dựng lại từ CSDL (chỉ adapter dùng). */
    public static Builder restore() {
        return new Builder();
    }

    // ===== Hành vi của chủ bãi xe =====

    public void cancel(long actorId, Instant now) {
        requireOwner(actorId);
        moveTo(WithdrawalStatus.DA_HUY);
        cancelledAt = at(now);
    }

    public void confirmReceived(long actorId, Instant now) {
        requireOwner(actorId);
        if (status != WithdrawalStatus.DA_TRA) {             // xác nhận sau khiếu nại là việc của quản lý
            throw new DomainException(ErrorCode.WITHDRAWAL_STATUS_CONFLICT);
        }
        moveTo(WithdrawalStatus.DA_XAC_NHAN);
        confirmedAt = at(now);
    }

    /** "Báo chưa nhận đủ tiền". KHÔNG tự hoàn điểm (R10) — quản lý kiểm tra, sai thì ghi giao dịch điều chỉnh. */
    public void complain(long actorId, String note, Instant now) {
        requireOwner(actorId);
        String text = requireText(note);
        moveTo(WithdrawalStatus.KHIEU_NAI);
        complaintNote = text;
        complainedAt = at(now);
    }

    // ===== Hành vi của quản lý =====

    /** Quản lý đã trả tiền mặt; {@code transactionId} là giao dịch RUT_DIEM vừa ghi (R17: mỗi yêu cầu một giao dịch). */
    public void markPaid(long managerId, long transactionId, Instant now) {
        requireNotOwner(managerId);
        moveTo(WithdrawalStatus.DA_TRA);
        this.processedBy = managerId;
        this.processedAt = at(now);
        this.transactionId = transactionId;
    }

    public void reject(long managerId, String reason, Instant now) {
        requireNotOwner(managerId);
        String text = requireText(reason);
        moveTo(WithdrawalStatus.TU_CHOI);
        this.processedBy = managerId;
        this.processedAt = at(now);
        this.rejectReason = text;
    }

    /** Kiểm tra xong khiếu nại (đã đối chiếu, nếu sai đã ghi giao dịch điều chỉnh) → đóng yêu cầu. */
    public void resolveComplaint(long managerId, String note, Instant now) {
        requireNotOwner(managerId);
        if (status != WithdrawalStatus.KHIEU_NAI) {
            throw new DomainException(ErrorCode.WITHDRAWAL_STATUS_CONFLICT);
        }
        String text = requireText(note);
        moveTo(WithdrawalStatus.DA_XAC_NHAN);
        this.resolutionNote = text;
        this.resolvedBy = managerId;
        this.resolvedAt = at(now);
    }

    // ===== Tiện ích =====

    /** Số tiền mặt sẽ trả / đã trả = số điểm × tỷ lệ lúc tạo. */
    public long cashAmount() {
        return rate.cashFor(points);
    }

    public WithdrawalRequest withId(long newId) {
        Builder b = toBuilder();
        b.id = newId;
        return new WithdrawalRequest(b);
    }

    private void moveTo(WithdrawalStatus next) {
        if (!status.canMoveTo(next)) {
            throw new DomainException(ErrorCode.WITHDRAWAL_STATUS_CONFLICT);
        }
        status = next;
    }

    private void requireOwner(long actorId) {
        if (actorId != ownerId) {
            throw new DomainException(ErrorCode.WITHDRAWAL_NOT_FOUND);     // không tiết lộ yêu cầu của người khác
        }
    }

    /** R18. */
    private void requireNotOwner(long actorId) {
        if (actorId == ownerId) {
            throw new DomainException(ErrorCode.SELF_PROCESSING_FORBIDDEN);
        }
    }

    private static String requireText(String value) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty() || text.length() > MAX_NOTE) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR);
        }
        return text;
    }

    private static Instant at(Instant now) {
        return now.truncatedTo(ChronoUnit.MICROS);
    }

    private Builder toBuilder() {
        Builder b = new Builder();
        b.id = id;
        b.ownerId = ownerId;
        b.points = points;
        b.rate = rate;
        b.createdAt = createdAt;
        b.status = status;
        b.cancelledAt = cancelledAt;
        b.processedBy = processedBy;
        b.processedAt = processedAt;
        b.rejectReason = rejectReason;
        b.transactionId = transactionId;
        b.confirmedAt = confirmedAt;
        b.complaintNote = complaintNote;
        b.complainedAt = complainedAt;
        b.resolutionNote = resolutionNote;
        b.resolvedBy = resolvedBy;
        b.resolvedAt = resolvedAt;
        return b;
    }

    public Long id() { return id; }

    public long ownerId() { return ownerId; }

    public long points() { return points; }

    public ExchangeRate rate() { return rate; }

    public Instant createdAt() { return createdAt; }

    public WithdrawalStatus status() { return status; }

    public Instant cancelledAt() { return cancelledAt; }

    public Long processedBy() { return processedBy; }

    public Instant processedAt() { return processedAt; }

    public String rejectReason() { return rejectReason; }

    public Long transactionId() { return transactionId; }

    public Instant confirmedAt() { return confirmedAt; }

    public String complaintNote() { return complaintNote; }

    public Instant complainedAt() { return complainedAt; }

    public String resolutionNote() { return resolutionNote; }

    public Long resolvedBy() { return resolvedBy; }

    public Instant resolvedAt() { return resolvedAt; }

    /**
     * Mẫu Builder để dựng lại yêu cầu từ CSDL (17 trường — constructor 17 tham số rất dễ truyền nhầm thứ tự).
     * Không dùng để tạo yêu cầu mới: yêu cầu mới chỉ sinh ra qua {@link #create}.
     */
    public static final class Builder {
        private Long id;
        private long ownerId;
        private long points;
        private ExchangeRate rate;
        private Instant createdAt;
        private WithdrawalStatus status;
        private Instant cancelledAt;
        private Long processedBy;
        private Instant processedAt;
        private String rejectReason;
        private Long transactionId;
        private Instant confirmedAt;
        private String complaintNote;
        private Instant complainedAt;
        private String resolutionNote;
        private Long resolvedBy;
        private Instant resolvedAt;

        private Builder() {
        }

        public Builder id(long value) { id = value; return this; }
        public Builder ownerId(long value) { ownerId = value; return this; }
        public Builder points(long value) { points = value; return this; }
        public Builder rate(ExchangeRate value) { rate = value; return this; }
        public Builder createdAt(Instant value) { createdAt = value; return this; }
        public Builder status(WithdrawalStatus value) { status = value; return this; }
        public Builder cancelledAt(Instant value) { cancelledAt = value; return this; }
        public Builder processedBy(Long value) { processedBy = value; return this; }
        public Builder processedAt(Instant value) { processedAt = value; return this; }
        public Builder rejectReason(String value) { rejectReason = value; return this; }
        public Builder transactionId(Long value) { transactionId = value; return this; }
        public Builder confirmedAt(Instant value) { confirmedAt = value; return this; }
        public Builder complaintNote(String value) { complaintNote = value; return this; }
        public Builder complainedAt(Instant value) { complainedAt = value; return this; }
        public Builder resolutionNote(String value) { resolutionNote = value; return this; }
        public Builder resolvedBy(Long value) { resolvedBy = value; return this; }
        public Builder resolvedAt(Instant value) { resolvedAt = value; return this; }

        public WithdrawalRequest build() {
            return new WithdrawalRequest(this);
        }
    }
}
