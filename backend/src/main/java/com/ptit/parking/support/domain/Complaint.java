package com.ptit.parking.support.domain;

import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Khiếu nại của sinh viên. Đóng gói: trạng thái chỉ đổi qua các hành vi nghiệp vụ, mỗi hành vi tự kiểm tra
 * ĐÚNG NGƯỜI, ĐÚNG TRẠNG THÁI ({@link ComplaintStatus#canMoveTo}) và ĐÚNG LOẠI ({@link ComplaintType}).
 * <ul>
 *   <li>Sinh viên: gửi, hủy khi quản lý chưa tiếp nhận.</li>
 *   <li>Quản lý: tiếp nhận, kết luận (kèm hoàn phí nếu thu sai), từ chối, đề xuất bồi thường.</li>
 *   <li>Chủ bãi xe: duyệt / từ chối bồi thường — người duyệt KHÁC người đề xuất (hai người cùng chịu trách nhiệm).</li>
 * </ul>
 */
public final class Complaint {

    public static final int MAX_TEXT = 1000;
    public static final int MAX_NOTE = 500;
    public static final long MAX_COMPENSATION = 10_000_000;

    private final Long id;
    private final long studentId;
    private final ComplaintType type;
    private final Long sessionId;
    private final String description;
    private final Instant createdAt;
    private final boolean hasPhoto;
    private ComplaintStatus status;
    private Instant cancelledAt;
    private Long handledBy;
    private Instant handledAt;
    private String resolution;
    private Long refundTransactionId;
    private Long proposedPoints;
    private Long proposedBy;
    private Instant proposedAt;
    private Long decidedBy;
    private Instant decidedAt;
    private String decisionNote;
    private Long compensationTransactionId;

    private Complaint(Builder b) {
        this.id = b.id;
        this.studentId = b.studentId;
        this.type = Objects.requireNonNull(b.type, "type");
        this.sessionId = b.sessionId;
        this.description = requireText(b.description, MAX_TEXT);
        this.createdAt = Objects.requireNonNull(b.createdAt, "createdAt");
        this.hasPhoto = b.hasPhoto;
        this.status = Objects.requireNonNull(b.status, "status");
        this.cancelledAt = b.cancelledAt;
        this.handledBy = b.handledBy;
        this.handledAt = b.handledAt;
        this.resolution = b.resolution;
        this.refundTransactionId = b.refundTransactionId;
        this.proposedPoints = b.proposedPoints;
        this.proposedBy = b.proposedBy;
        this.proposedAt = b.proposedAt;
        this.decidedBy = b.decidedBy;
        this.decidedAt = b.decidedAt;
        this.decisionNote = b.decisionNote;
        this.compensationTransactionId = b.compensationTransactionId;
    }

    // ===== Sinh viên =====

    public static Complaint submit(long studentId, ComplaintType type, Long sessionId, String description,
                                   boolean hasPhoto, Instant now) {
        return restore().studentId(studentId).type(type).sessionId(sessionId).description(description)
                .hasPhoto(hasPhoto).createdAt(micros(now)).status(ComplaintStatus.MOI).build();
    }

    /** Sinh viên rút lại khiếu nại khi quản lý CHƯA tiếp nhận. */
    public void cancel(long requesterId, Instant now) {
        requireOwner(requesterId);
        moveTo(ComplaintStatus.DA_HUY);
        cancelledAt = micros(now);
    }

    // ===== Quản lý =====

    public void startHandling(long managerId, Instant now) {
        moveTo(ComplaintStatus.DANG_XU_LY);
        handledBy = managerId;
        handledAt = micros(now);
    }

    /**
     * Kết luận. {@code refundTransactionId}: giao dịch hoàn phí đã ghi (chỉ loại thu phí sai), null nếu không hoàn.
     */
    public void resolve(long managerId, String result, Long refundTransactionId, Instant now) {
        if (refundTransactionId != null && !type.allowsRefund()) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR);
        }
        String text = requireText(result, MAX_TEXT);
        moveTo(ComplaintStatus.DA_XU_LY);
        resolution = text;
        this.refundTransactionId = refundTransactionId;
        handledBy = managerId;
        handledAt = micros(now);
    }

    public void reject(long managerId, String reason, Instant now) {
        String text = requireText(reason, MAX_TEXT);
        moveTo(ComplaintStatus.TU_CHOI);
        resolution = text;
        handledBy = managerId;
        handledAt = micros(now);
    }

    /** Chỉ xe hư hỏng / mất đồ; số điểm 1 .. 10.000.000; ghi rõ căn cứ. */
    public void proposeCompensation(long managerId, long points, String basis, Instant now) {
        if (!type.allowsCompensation()) {
            throw new DomainException(ErrorCode.COMPLAINT_NOT_COMPENSABLE);
        }
        if (points <= 0 || points > MAX_COMPENSATION) {
            throw new DomainException(ErrorCode.INVALID_AMOUNT);
        }
        String text = requireText(basis, MAX_TEXT);
        moveTo(ComplaintStatus.CHO_DUYET_BOI_THUONG);
        resolution = text;
        proposedPoints = points;
        proposedBy = managerId;
        proposedAt = micros(now);
    }

    // ===== Chủ bãi xe =====

    /** Duyệt: {@code transactionId} là giao dịch điều chỉnh chủ bãi → sinh viên vừa ghi. */
    public void approveCompensation(long ownerId, String note, long transactionId, Instant now) {
        requireNotProposer(ownerId);
        String text = note == null || note.isBlank() ? null : requireText(note, MAX_NOTE);
        moveTo(ComplaintStatus.DA_BOI_THUONG);
        decide(ownerId, text, now);
        compensationTransactionId = transactionId;
    }

    /** Từ chối: bắt buộc ghi lý do để quản lý và sinh viên biết. */
    public void rejectCompensation(long ownerId, String reason, Instant now) {
        requireNotProposer(ownerId);
        String text = requireText(reason, MAX_NOTE);
        moveTo(ComplaintStatus.KHONG_BOI_THUONG);
        decide(ownerId, text, now);
    }

    /** Kiểm tra TRƯỚC khi chuyển tiền: đúng trạng thái và người duyệt khác người đề xuất. */
    public void requireAwaitingDecisionBy(long ownerId) {
        requireNotProposer(ownerId);
        if (status != ComplaintStatus.CHO_DUYET_BOI_THUONG) {
            throw new DomainException(ErrorCode.COMPLAINT_STATUS_CONFLICT);
        }
    }

    public boolean isOpen() {
        return !status.isClosed();
    }

    public boolean submittedBy(long userId) {
        return studentId == userId;
    }

    public Complaint withId(long newId) {
        return toBuilder().id(newId).build();
    }

    private void decide(long ownerId, String note, Instant now) {
        decidedBy = ownerId;
        decidedAt = micros(now);
        decisionNote = note;
    }

    private void requireNotProposer(long userId) {
        if (proposedBy != null && proposedBy == userId) {
            throw new DomainException(ErrorCode.SELF_PROCESSING_FORBIDDEN);
        }
    }

    private void requireOwner(long requesterId) {
        if (studentId != requesterId) {
            throw new DomainException(ErrorCode.COMPLAINT_NOT_FOUND);       // không tiết lộ khiếu nại của người khác
        }
    }

    private void moveTo(ComplaintStatus next) {
        if (!status.canMoveTo(next)) {
            throw new DomainException(ErrorCode.COMPLAINT_STATUS_CONFLICT);
        }
        status = next;
    }

    private static String requireText(String value, int max) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty() || text.length() > max) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR);
        }
        return text;
    }

    private static Instant micros(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS);
    }

    // ===== Truy cập =====

    public Long id() { return id; }

    public long studentId() { return studentId; }

    public ComplaintType type() { return type; }

    public Long sessionId() { return sessionId; }

    public String description() { return description; }

    public Instant createdAt() { return createdAt; }

    public boolean hasPhoto() { return hasPhoto; }

    public ComplaintStatus status() { return status; }

    public Instant cancelledAt() { return cancelledAt; }

    public Long handledBy() { return handledBy; }

    public Instant handledAt() { return handledAt; }

    public String resolution() { return resolution; }

    public Long refundTransactionId() { return refundTransactionId; }

    public Long proposedPoints() { return proposedPoints; }

    public Long proposedBy() { return proposedBy; }

    public Instant proposedAt() { return proposedAt; }

    public Long decidedBy() { return decidedBy; }

    public Instant decidedAt() { return decidedAt; }

    public String decisionNote() { return decisionNote; }

    public Long compensationTransactionId() { return compensationTransactionId; }

    // ===== Builder: dựng lại 20 trường từ CSDL mà không nhầm thứ tự tham số =====

    public static Builder restore() {
        return new Builder();
    }

    private Builder toBuilder() {
        return restore().id(id).studentId(studentId).type(type).sessionId(sessionId).description(description)
                .createdAt(createdAt).hasPhoto(hasPhoto).status(status).cancelledAt(cancelledAt).handledBy(handledBy)
                .handledAt(handledAt).resolution(resolution).refundTransactionId(refundTransactionId)
                .proposedPoints(proposedPoints).proposedBy(proposedBy).proposedAt(proposedAt).decidedBy(decidedBy)
                .decidedAt(decidedAt).decisionNote(decisionNote).compensationTransactionId(compensationTransactionId);
    }

    public static final class Builder {
        private Long id;
        private long studentId;
        private ComplaintType type;
        private Long sessionId;
        private String description;
        private Instant createdAt;
        private boolean hasPhoto;
        private ComplaintStatus status;
        private Instant cancelledAt;
        private Long handledBy;
        private Instant handledAt;
        private String resolution;
        private Long refundTransactionId;
        private Long proposedPoints;
        private Long proposedBy;
        private Instant proposedAt;
        private Long decidedBy;
        private Instant decidedAt;
        private String decisionNote;
        private Long compensationTransactionId;

        private Builder() {
        }

        public Builder id(Long value) { id = value; return this; }

        public Builder studentId(long value) { studentId = value; return this; }

        public Builder type(ComplaintType value) { type = value; return this; }

        public Builder sessionId(Long value) { sessionId = value; return this; }

        public Builder description(String value) { description = value; return this; }

        public Builder createdAt(Instant value) { createdAt = value; return this; }

        public Builder hasPhoto(boolean value) { hasPhoto = value; return this; }

        public Builder status(ComplaintStatus value) { status = value; return this; }

        public Builder cancelledAt(Instant value) { cancelledAt = value; return this; }

        public Builder handledBy(Long value) { handledBy = value; return this; }

        public Builder handledAt(Instant value) { handledAt = value; return this; }

        public Builder resolution(String value) { resolution = value; return this; }

        public Builder refundTransactionId(Long value) { refundTransactionId = value; return this; }

        public Builder proposedPoints(Long value) { proposedPoints = value; return this; }

        public Builder proposedBy(Long value) { proposedBy = value; return this; }

        public Builder proposedAt(Instant value) { proposedAt = value; return this; }

        public Builder decidedBy(Long value) { decidedBy = value; return this; }

        public Builder decidedAt(Instant value) { decidedAt = value; return this; }

        public Builder decisionNote(String value) { decisionNote = value; return this; }

        public Builder compensationTransactionId(Long value) { compensationTransactionId = value; return this; }

        public Complaint build() {
            return new Complaint(this);
        }
    }
}
