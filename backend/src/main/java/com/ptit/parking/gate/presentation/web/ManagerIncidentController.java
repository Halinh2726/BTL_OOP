package com.ptit.parking.gate.presentation.web;

import com.ptit.parking.gate.application.usecase.HandleIncidentUseCase;
import com.ptit.parking.gate.application.usecase.HandleIncidentUseCase.Listed;
import com.ptit.parking.gate.domain.Incident;
import com.ptit.parking.gate.domain.IncidentPhoto;
import com.ptit.parking.gate.domain.IncidentStatus;
import com.ptit.parking.shared.infrastructure.security.SessionUser;
import com.ptit.parking.student.domain.PlateNumber;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Đợt 4b — quản lý xử lý sự cố bảo vệ báo (quyền CASE_HANDLE). */
@RestController
@RequestMapping("/api/v1/manager/incidents")
class ManagerIncidentController {

    record NoteRequest(@Size(max = Incident.MAX_TEXT, message = "Tối đa 1000 ký tự") String note) {}

    record ManagedIncident(long id, String type, String plateNumber, String plateDisplay, String description,
                           String status, String createdAt, String reporterName, boolean hasPhoto, String handlerName,
                           String handledAt, String resolution) {}

    private final HandleIncidentUseCase incidents;
    private final ZoneId zone;

    ManagerIncidentController(HandleIncidentUseCase incidents, Clock clock) {
        this.incidents = incidents;
        this.zone = clock.getZone();
    }

    @GetMapping
    List<ManagedIncident> list(@RequestParam(required = false) IncidentStatus status) {
        return incidents.list(status).stream().map(this::view).toList();
    }

    @PostMapping("/{id}/start")
    ManagedIncident start(@AuthenticationPrincipal SessionUser manager, @PathVariable long id) {
        return view(incidents.start(manager.id(), id));
    }

    @PostMapping("/{id}/resolve")
    ManagedIncident resolve(@AuthenticationPrincipal SessionUser manager, @PathVariable long id,
                            @RequestBody NoteRequest body) {
        return view(incidents.resolve(manager.id(), id, body.note()));
    }

    @PostMapping("/{id}/reject")
    ManagedIncident reject(@AuthenticationPrincipal SessionUser manager, @PathVariable long id,
                           @RequestBody NoteRequest body) {
        return view(incidents.reject(manager.id(), id, body.note()));
    }

    @GetMapping("/{id}/photo")
    ResponseEntity<byte[]> photo(@PathVariable long id) {
        IncidentPhoto photo = incidents.photo(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.noStore()).body(photo.data());
    }

    private ManagedIncident view(Listed listed) {
        Incident i = listed.incident();
        return new ManagedIncident(i.id(), i.type().name(), i.plate().map(PlateNumber::value).orElse(null),
                i.plate().map(PlateNumber::display).orElse(null), i.description(), i.status().name(),
                GateResponses.time(i.createdAt(), zone), listed.reporterName(), i.hasPhoto(), listed.handlerName(),
                GateResponses.time(i.handledAt(), zone), i.resolution());
    }
}
