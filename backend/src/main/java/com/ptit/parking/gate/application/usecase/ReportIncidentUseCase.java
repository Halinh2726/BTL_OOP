package com.ptit.parking.gate.application.usecase;

import com.ptit.parking.gate.application.port.out.IncidentRepository;
import com.ptit.parking.gate.domain.Incident;
import com.ptit.parking.gate.domain.IncidentPhoto;
import com.ptit.parking.gate.domain.IncidentType;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.student.domain.PlateNumber;
import java.time.Clock;
import java.util.List;

/**
 * Đợt 2 — bảo vệ báo sự cố (kèm ảnh nếu có) và xem các sự cố mình đã báo. Người báo luôn là bảo vệ trong phiên.
 * Ảnh chỉ người báo xem được (quản lý xem ở Đợt 4).
 */
public class ReportIncidentUseCase {

    public static final int MY_INCIDENTS_LIMIT = 50;

    /** {@code plateNumber}, {@code photoBase64} không bắt buộc. */
    public record Command(long guardId, IncidentType type, String plateNumber, String description,
                          String photoContentType, String photoBase64) {}

    private final IncidentRepository incidents;
    private final TransactionRunner transactions;
    private final Clock clock;

    public ReportIncidentUseCase(IncidentRepository incidents, TransactionRunner transactions, Clock clock) {
        this.incidents = incidents;
        this.transactions = transactions;
        this.clock = clock;
    }

    public Incident report(Command command) {
        PlateNumber plate = command.plateNumber() == null || command.plateNumber().isBlank()
                ? null
                : PlateNumber.tryParse(command.plateNumber()).orElseThrow(() -> new DomainException(ErrorCode.VALIDATION_ERROR));
        IncidentPhoto photo = command.photoBase64() == null || command.photoBase64().isBlank()
                ? null
                : IncidentPhoto.fromBase64(command.photoContentType(), command.photoBase64());
        Incident incident = Incident.report(command.guardId(), command.type(), plate, command.description(),
                photo != null, clock.instant());
        return transactions.inTransaction(() -> incidents.save(incident, photo));
    }

    public List<Incident> mine(long guardId) {
        return incidents.findByReporter(guardId, MY_INCIDENTS_LIMIT);
    }

    /** Ảnh của một sự cố MÌNH báo; sự cố của người khác → INCIDENT_NOT_FOUND (không tiết lộ là có tồn tại). */
    public IncidentPhoto photo(long guardId, long incidentId) {
        incidents.findById(incidentId)
                .filter(incident -> incident.reportedBy(guardId))
                .orElseThrow(() -> new DomainException(ErrorCode.INCIDENT_NOT_FOUND));
        return incidents.findPhoto(incidentId).orElseThrow(() -> new DomainException(ErrorCode.INCIDENT_NOT_FOUND));
    }
}
