package com.ptit.parking.support.presentation.web;

import com.ptit.parking.gate.domain.IncidentPhoto;
import com.ptit.parking.shared.infrastructure.security.SessionUser;
import com.ptit.parking.support.presentation.web.ComplaintView.SessionView;
import com.ptit.parking.support.application.usecase.StudentComplaintUseCase;
import com.ptit.parking.support.domain.Complaint;
import com.ptit.parking.support.domain.ComplaintType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Đợt 4b — sinh viên gửi / xem / hủy khiếu nại (quyền COMPLAINT_SUBMIT). Luôn là khiếu nại của CHÍNH mình. */
@RestController
class MyComplaintController {

    record SubmitRequest(
            @NotNull(message = "Vui lòng chọn loại khiếu nại") ComplaintType type,
            @Positive Long sessionId,
            @NotBlank(message = "Vui lòng mô tả sự việc")
            @Size(max = Complaint.MAX_TEXT, message = "Mô tả tối đa 1000 ký tự") String description,
            @Pattern(regexp = "image/(jpeg|png|webp)", message = "Ảnh phải là JPG, PNG hoặc WEBP") String photoContentType,
            // Base64 của ảnh tối đa 2 MB ~ 2,8 triệu ký tự
            @Size(max = 2_900_000, message = "Ảnh tối đa 2 MB") String photoBase64,
            Boolean cameraRequested,
            @Size(max = Complaint.MAX_AREA, message = "Khu vực tối đa 150 ký tự") String incidentArea,
            @Size(max = Complaint.MAX_TIME_RANGE, message = "Khung giờ tối đa 100 ký tự") String incidentTimeRange) {}

    private final StudentComplaintUseCase complaints;
    private final ZoneId zone;

    MyComplaintController(StudentComplaintUseCase complaints, Clock clock) {
        this.complaints = complaints;
        this.zone = clock.getZone();
    }

    @GetMapping("/api/v1/me/complaints")
    List<ComplaintView> mine(@AuthenticationPrincipal SessionUser student) {
        return complaints.mine(student.id()).stream().map(d -> ComplaintView.of(d, zone)).toList();
    }

    @PostMapping("/api/v1/me/complaints")
    @ResponseStatus(HttpStatus.CREATED)
    ComplaintView submit(@AuthenticationPrincipal SessionUser student, @Valid @RequestBody SubmitRequest body) {
        return ComplaintView.of(complaints.submit(new StudentComplaintUseCase.Command(
                student.id(),
                body.type(),
                body.sessionId(),
                body.description(),
                body.photoContentType(),
                body.photoBase64(),
                Boolean.TRUE.equals(body.cameraRequested()),
                body.incidentArea(),
                body.incidentTimeRange())), zone);
    }

    @PostMapping("/api/v1/me/complaints/{id}/cancel")
    ComplaintView cancel(@AuthenticationPrincipal SessionUser student, @PathVariable long id) {
        return ComplaintView.of(complaints.cancel(student.id(), id), zone);
    }

    @GetMapping("/api/v1/me/complaints/{id}/photo")
    ResponseEntity<byte[]> photo(@AuthenticationPrincipal SessionUser student, @PathVariable long id) {
        IncidentPhoto photo = complaints.photo(student.id(), id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.noStore()).body(photo.data());
    }

    @GetMapping("/api/v1/me/complaints/{id}/camera-photo")
    ResponseEntity<byte[]> cameraPhoto(@AuthenticationPrincipal SessionUser student, @PathVariable long id) {
        IncidentPhoto photo = complaints.cameraPhoto(student.id(), id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.noStore()).body(photo.data());
    }

    /** Các lượt gửi gần đây để chọn khi gửi khiếu nại. */
    @GetMapping("/api/v1/me/complaint-sessions")
    List<SessionView> sessions(@AuthenticationPrincipal SessionUser student) {
        return complaints.recentSessions(student.id()).stream()
                .map(s -> new SessionView(s.id(), s.plateNumber(), ComplaintView.time(s.enteredAt(), zone),
                        ComplaintView.time(s.exitedAt(), zone), s.fee()))
                .toList();
    }
}
