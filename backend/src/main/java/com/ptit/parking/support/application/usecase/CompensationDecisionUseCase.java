package com.ptit.parking.support.application.usecase;

import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.support.application.PointPayout;
import com.ptit.parking.support.application.port.out.ComplaintDirectory;
import com.ptit.parking.support.application.port.out.ComplaintRepository;
import com.ptit.parking.support.domain.Complaint;
import com.ptit.parking.support.domain.ComplaintStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

/**
 * Đợt 4b — chủ bãi xe quyết định bồi thường do quản lý đề xuất (xe hư hỏng, mất đồ trong bãi). Tiền bồi thường là
 * ĐIỂM từ ví chủ bãi chuyển sang ví sinh viên (giao dịch điều chỉnh) — chủ bãi chịu nên chủ bãi duyệt.
 * <p>Duyệt: khóa khiếu nại → kiểm tra trạng thái và người duyệt ≠ người đề xuất → chuyển điểm → đóng khiếu nại,
 * tất cả trong MỘT transaction. Bấm "Duyệt" lần hai: khiếu nại đã ở DA_BOI_THUONG → trả lại kết quả cũ, không trả
 * điểm lần hai.
 */
public class CompensationDecisionUseCase {

    public static final int LIST_LIMIT = 50;

    public record Decision(ComplaintDetails details, boolean replayed) {}

    private final ComplaintRepository complaints;
    private final ComplaintDirectory directory;
    private final PointPayout payout;
    private final TransactionRunner transactions;
    private final Clock clock;

    public CompensationDecisionUseCase(ComplaintRepository complaints, ComplaintDirectory directory, PointPayout payout,
                                       TransactionRunner transactions, Clock clock) {
        this.complaints = complaints;
        this.directory = directory;
        this.payout = payout;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** Đề xuất đang chờ duyệt trước, rồi các quyết định gần đây. */
    public List<ComplaintDetails> list() {
        return ComplaintDetails.of(complaints.findByStatuses(EnumSet.of(ComplaintStatus.CHO_DUYET_BOI_THUONG,
                ComplaintStatus.DA_BOI_THUONG, ComplaintStatus.KHONG_BOI_THUONG), LIST_LIMIT), directory)
                .stream()
                .sorted((a, b) -> Boolean.compare(b.complaint().isOpen(), a.complaint().isOpen()))
                .toList();
    }

    public Decision approve(long ownerId, long complaintId, String note) {
        boolean[] replayed = {false};
        Complaint decided = transactions.inTransaction(() -> {
            Complaint c = lock(complaintId);
            if (c.status() == ComplaintStatus.DA_BOI_THUONG) {
                replayed[0] = true;                                   // đã duyệt trước đó: không trả điểm lần hai
                return c;
            }
            c.requireAwaitingDecisionBy(ownerId);
            Instant now = clock.instant();
            long transactionId = payout.payStudent(c.studentId(), c.proposedPoints(),
                    "Bồi thường theo khiếu nại #" + c.id(), null, ownerId, "complaint-compensation:" + c.id(), now);
            c.approveCompensation(ownerId, note, transactionId, now);
            complaints.update(c);
            return c;
        });
        return new Decision(ComplaintDetails.of(decided, directory), replayed[0]);
    }

    public ComplaintDetails reject(long ownerId, long complaintId, String reason) {
        Complaint decided = transactions.inTransaction(() -> {
            Complaint c = lock(complaintId);
            c.rejectCompensation(ownerId, reason, clock.instant());
            complaints.update(c);
            return c;
        });
        return ComplaintDetails.of(decided, directory);
    }

    private Complaint lock(long complaintId) {
        return complaints.lockById(complaintId).orElseThrow(() -> new DomainException(ErrorCode.COMPLAINT_NOT_FOUND));
    }
}
