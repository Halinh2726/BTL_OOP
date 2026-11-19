package com.ptit.parking.support.application.usecase;

import com.ptit.parking.gate.domain.IncidentPhoto;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.support.application.port.out.ComplaintDirectory;
import com.ptit.parking.support.application.port.out.ComplaintDirectory.SessionInfo;
import com.ptit.parking.support.application.port.out.ComplaintRepository;
import com.ptit.parking.support.domain.Complaint;
import com.ptit.parking.support.domain.ComplaintType;
import java.time.Clock;
import java.util.List;

/**
 * Đợt 4b — sinh viên gửi khiếu nại (thu phí sai, xe hư hỏng, mất đồ...) kèm ảnh và lượt gửi liên quan, theo dõi kết
 * quả, hủy khi quản lý chưa tiếp nhận. Lượt gửi phải là của CHÍNH sinh viên đó.
 */
public class StudentComplaintUseCase {

    public static final int MY_LIMIT = 50;
    public static final int SESSION_CHOICES = 10;
    /** Tránh gửi tràn lan: tối đa 5 khiếu nại đang mở cùng lúc. */
    public static final int MAX_OPEN = 5;

    public record Command(long studentId, ComplaintType type, Long sessionId, String description,
                          String photoContentType, String photoBase64) {}

    private final ComplaintRepository complaints;
    private final ComplaintDirectory directory;
    private final TransactionRunner transactions;
    private final Clock clock;

    public StudentComplaintUseCase(ComplaintRepository complaints, ComplaintDirectory directory,
                                   TransactionRunner transactions, Clock clock) {
        this.complaints = complaints;
        this.directory = directory;
        this.transactions = transactions;
        this.clock = clock;
    }

    public ComplaintDetails submit(Command command) {
        if (command.sessionId() != null
                && directory.sessionOfStudent(command.sessionId(), command.studentId()).isEmpty()) {
            throw new DomainException(ErrorCode.PARKING_SESSION_NOT_FOUND);
        }
        IncidentPhoto photo = command.photoBase64() == null || command.photoBase64().isBlank()
                ? null : IncidentPhoto.fromBase64(command.photoContentType(), command.photoBase64());
        Complaint complaint = Complaint.submit(command.studentId(), command.type(), command.sessionId(),
                command.description(), photo != null, clock.instant());
        Complaint saved = transactions.inTransaction(() -> {
            long open = complaints.findByStudent(command.studentId(), MY_LIMIT).stream().filter(Complaint::isOpen).count();
            if (open >= MAX_OPEN) {
                throw new DomainException(ErrorCode.TOO_MANY_OPEN_COMPLAINTS);
            }
            return complaints.insert(complaint, photo);
        });
        return ComplaintDetails.of(saved, directory);
    }

    public List<ComplaintDetails> mine(long studentId) {
        return ComplaintDetails.of(complaints.findByStudent(studentId, MY_LIMIT), directory);
    }

    public ComplaintDetails cancel(long studentId, long complaintId) {
        Complaint complaint = transactions.inTransaction(() -> {
            Complaint c = complaints.lockById(complaintId)
                    .orElseThrow(() -> new DomainException(ErrorCode.COMPLAINT_NOT_FOUND));
            c.cancel(studentId, clock.instant());
            complaints.update(c);
            return c;
        });
        return ComplaintDetails.of(complaint, directory);
    }

    /** Ảnh của khiếu nại MÌNH gửi; của người khác → COMPLAINT_NOT_FOUND. */
    public IncidentPhoto photo(long studentId, long complaintId) {
        complaints.findById(complaintId).filter(c -> c.submittedBy(studentId))
                .orElseThrow(() -> new DomainException(ErrorCode.COMPLAINT_NOT_FOUND));
        return complaints.findPhoto(complaintId).orElseThrow(() -> new DomainException(ErrorCode.COMPLAINT_NOT_FOUND));
    }

    /** Các lượt gửi gần đây để chọn khi gửi khiếu nại. */
    public List<SessionInfo> recentSessions(long studentId) {
        return directory.recentSessions(studentId, SESSION_CHOICES);
    }
}
