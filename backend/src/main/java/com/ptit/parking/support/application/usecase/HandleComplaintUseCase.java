package com.ptit.parking.support.application.usecase;

import com.ptit.parking.support.application.event.ComplaintChanged;
import com.ptit.parking.shared.application.port.out.EventPublisher;
import com.ptit.parking.gate.domain.IncidentPhoto;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.support.application.PointPayout;
import com.ptit.parking.support.application.port.out.ComplaintDirectory;
import com.ptit.parking.support.application.port.out.ComplaintDirectory.SessionInfo;
import com.ptit.parking.support.application.port.out.ComplaintRepository;
import com.ptit.parking.support.domain.Complaint;
import com.ptit.parking.support.domain.ComplaintStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Đợt 4b — quản lý xử lý khiếu nại của sinh viên: tiếp nhận, kết luận, từ chối, đề xuất bồi thường.
 * <p>Thu phí sai: khi kết luận có thể HOÀN PHÍ ngay — tối đa bằng phí đã thu của lượt gửi liên quan, ghi một giao dịch
 * điều chỉnh trỏ tới giao dịch thanh toán gốc (R10: không sửa giao dịch cũ). Hoàn phí và đóng khiếu nại nằm trong CÙNG
 * transaction.
 */
public class HandleComplaintUseCase {

    public static final int LIST_LIMIT = 100;
    private static final Set<ComplaintStatus> OPEN =
            EnumSet.of(ComplaintStatus.MOI, ComplaintStatus.DANG_XU_LY, ComplaintStatus.CHO_DUYET_BOI_THUONG);

    private final ComplaintRepository complaints;
    private final ComplaintDirectory directory;
    private final PointPayout payout;
    private final TransactionRunner transactions;
    private final EventPublisher events;
    private final Clock clock;

    public HandleComplaintUseCase(ComplaintRepository complaints, ComplaintDirectory directory, PointPayout payout,
                                  TransactionRunner transactions, EventPublisher events, Clock clock) {
        this.complaints = complaints;
        this.directory = directory;
        this.payout = payout;
        this.transactions = transactions;
        this.events = events;
        this.clock = clock;
    }

    /** {@code status} null = các khiếu nại còn mở (cần làm). */
    public List<ComplaintDetails> list(ComplaintStatus status) {
        Set<ComplaintStatus> filter = status == null ? OPEN : EnumSet.of(status);
        return ComplaintDetails.of(complaints.findByStatuses(filter, LIST_LIMIT), directory);
    }

    public ComplaintDetails start(long managerId, long complaintId) {
        return change(complaintId, c -> c.startHandling(managerId, clock.instant()));
    }

    public ComplaintDetails reject(long managerId, long complaintId, String reason) {
        return change(complaintId, c -> c.reject(managerId, reason, clock.instant()));
    }

    public ComplaintDetails propose(long managerId, long complaintId, long points, String basis) {
        return change(complaintId, c -> c.proposeCompensation(managerId, points, basis, clock.instant()));
    }

    /** Kết luận; {@code refundPoints} > 0 thì hoàn phí (chỉ khiếu nại thu phí sai có lượt gửi đã thanh toán). */
    public ComplaintDetails resolve(long managerId, long complaintId, String result, long refundPoints) {
        return change(complaintId, c -> {
            Instant now = clock.instant();
            Long refundId = null;
            if (refundPoints > 0) {
                SessionInfo session = c.sessionId() == null ? null
                        : directory.sessionsOf(List.of(c.sessionId())).get(c.sessionId());
                if (!c.type().allowsRefund() || session == null || session.fee() == null) {
                    throw new DomainException(ErrorCode.REFUND_NOT_ALLOWED);
                }
                if (refundPoints > session.fee()) {
                    throw new DomainException(ErrorCode.REFUND_EXCEEDS_FEE);
                }
                // Kiểm tra trạng thái TRƯỚC khi chuyển điểm (lỗi sau đó sẽ rollback cả hai)
                if (!c.status().canMoveTo(ComplaintStatus.DA_XU_LY)) {
                    throw new DomainException(ErrorCode.COMPLAINT_STATUS_CONFLICT);
                }
                refundId = payout.payStudent(c.studentId(), refundPoints, "Hoàn phí theo khiếu nại #" + c.id(),
                        session.paymentTransactionId(), managerId, "complaint-refund:" + c.id(), now);
            }
            c.resolve(managerId, result, refundId, now);
        });
    }

    public IncidentPhoto photo(long complaintId) {
        complaints.findById(complaintId).orElseThrow(() -> new DomainException(ErrorCode.COMPLAINT_NOT_FOUND));
        return complaints.findPhoto(complaintId).orElseThrow(() -> new DomainException(ErrorCode.COMPLAINT_NOT_FOUND));
    }

    private ComplaintDetails change(long complaintId, java.util.function.Consumer<Complaint> action) {
        Complaint changed = transactions.inTransaction(() -> {
            Complaint c = complaints.lockById(complaintId)
                    .orElseThrow(() -> new DomainException(ErrorCode.COMPLAINT_NOT_FOUND));
            action.accept(c);
            complaints.update(c);
            events.publish(ComplaintChanged.of(c));                          // Đợt 5: báo sinh viên / chủ bãi
            return c;
        });
        return ComplaintDetails.of(changed, directory);
    }
}
