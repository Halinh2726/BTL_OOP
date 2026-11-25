package com.ptit.parking.gate.application.usecase;

import com.ptit.parking.gate.application.event.IncidentChanged;
import com.ptit.parking.gate.application.port.out.IncidentRepository;
import com.ptit.parking.gate.domain.Incident;
import com.ptit.parking.gate.domain.IncidentPhoto;
import com.ptit.parking.gate.domain.IncidentStatus;
import com.ptit.parking.shared.application.port.out.EventPublisher;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Đợt 4b — quản lý xử lý sự cố bảo vệ đã báo (Đợt 2): nhận xử lý, kết luận, từ chối (báo nhầm). Quy tắc chuyển trạng
 * thái nằm trong {@link Incident}; use case chỉ khóa dòng, gọi hành vi và lưu.
 */
public class HandleIncidentUseCase {

    public static final int LIST_LIMIT = 100;

    /** Sự cố kèm tên người báo và người xử lý. */
    public record Listed(Incident incident, String reporterName, String handlerName) {}

    private final IncidentRepository incidents;
    private final TransactionRunner transactions;
    private final EventPublisher events;
    private final Clock clock;

    public HandleIncidentUseCase(IncidentRepository incidents, TransactionRunner transactions, Clock clock) {
        this(incidents, transactions, EventPublisher.NONE, clock);
    }

    public HandleIncidentUseCase(IncidentRepository incidents, TransactionRunner transactions,
                                 EventPublisher events, Clock clock) {
        this.incidents = incidents;
        this.transactions = transactions;
        this.events = events;
        this.clock = clock;
    }

    /** {@code status} null = các sự cố chưa đóng. */
    public List<Listed> list(IncidentStatus status) {
        return withNames(incidents.findByStatus(status, LIST_LIMIT));
    }

    public Listed start(long managerId, long incidentId) {
        return change(incidentId, i -> i.startHandling(managerId, clock.instant()));
    }

    public Listed resolve(long managerId, long incidentId, String result) {
        return change(incidentId, i -> i.resolve(managerId, result, clock.instant()));
    }

    public Listed reject(long managerId, long incidentId, String reason) {
        return change(incidentId, i -> i.reject(managerId, reason, clock.instant()));
    }

    public IncidentPhoto photo(long incidentId) {
        incidents.findById(incidentId).orElseThrow(() -> new DomainException(ErrorCode.INCIDENT_NOT_FOUND));
        return incidents.findPhoto(incidentId).orElseThrow(() -> new DomainException(ErrorCode.INCIDENT_NOT_FOUND));
    }

    private Listed change(long incidentId, Consumer<Incident> action) {
        Incident changed = transactions.inTransaction(() -> {
            Incident incident = incidents.lockById(incidentId)
                    .orElseThrow(() -> new DomainException(ErrorCode.INCIDENT_NOT_FOUND));
            action.accept(incident);
            incidents.updateHandling(incident);
            Long ownerId = incident.plate()
                    .flatMap(incidents::findActiveVehicleOwnerId)
                    .orElse(null);
            events.publish(IncidentChanged.of(incident, ownerId));
            return incident;
        });
        return withNames(List.of(changed)).getFirst();
    }

    private List<Listed> withNames(List<Incident> list) {
        Set<Long> ids = new HashSet<>();
        list.forEach(i -> {
            ids.add(i.reporterId());
            if (i.handledBy() != null) {
                ids.add(i.handledBy());
            }
        });
        Map<Long, String> names = incidents.namesOf(ids);
        return list.stream().map(i -> new Listed(i, names.get(i.reporterId()),
                i.handledBy() == null ? null : names.get(i.handledBy()))).toList();
    }
}
