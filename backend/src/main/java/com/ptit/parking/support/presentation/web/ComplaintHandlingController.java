package com.ptit.parking.support.presentation.web;

import com.ptit.parking.gate.domain.IncidentPhoto;
import com.ptit.parking.shared.infrastructure.security.SessionUser;
import com.ptit.parking.support.application.usecase.CompensationDecisionUseCase;
import com.ptit.parking.support.application.usecase.HandleComplaintUseCase;
import com.ptit.parking.support.domain.CameraReviewStatus;
import com.ptit.parking.support.domain.Complaint;
import com.ptit.parking.support.domain.ComplaintStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Đợt 4b — quản lý xử lý khiếu nại (quyền CASE_HANDLE) và chủ bãi xe quyết định bồi thường
 * (quyền COMPENSATION_APPROVE). Quyền theo từng đường dẫn nằm ở ProtectedEndpoint.
 */
@RestController
class ComplaintHandlingController {

    record NoteRequest(@Size(max = Complaint.MAX_TEXT, message = "Tối đa 1000 ký tự") String note) {}

    record ResolveRequest(@Size(max = Complaint.MAX_TEXT, message = "Tối đa 1000 ký tự") String note,
                          @PositiveOrZero(message = "Số điểm hoàn không được âm") Long refundPoints) {}

    record ProposeRequest(@NotNull(message = "Vui lòng nhập số điểm bồi thường") @Positive(message = "Số điểm phải > 0")
                          @Max(value = Complaint.MAX_COMPENSATION, message = "Tối đa 10.000.000 điểm") Long points,
                          @Size(max = Complaint.MAX_TEXT, message = "Tối đa 1000 ký tự") String note) {}

    record CameraReviewRequest(
            @NotNull(message = "Vui lòng chọn trạng thái trích xuất camera") CameraReviewStatus cameraStatus,
            @Size(max = Complaint.MAX_TEXT, message = "Ghi chú tối đa 1000 ký tự") String cameraNote,
            @Pattern(regexp = "image/(jpeg|png|webp)", message = "Ảnh phải là JPG, PNG hoặc WEBP") String photoContentType,
            @Size(max = 2_900_000, message = "Ảnh tối đa 2 MB") String photoBase64) {}

    record DecisionResponse(ComplaintView complaint, boolean replayed) {}

    private final HandleComplaintUseCase handling;
    private final CompensationDecisionUseCase decisions;
    private final ZoneId zone;

    ComplaintHandlingController(HandleComplaintUseCase handling, CompensationDecisionUseCase decisions, Clock clock) {
        this.handling = handling;
        this.decisions = decisions;
        this.zone = clock.getZone();
    }

    // ===== Quản lý =====

    @GetMapping("/api/v1/manager/complaints")
    List<ComplaintView> list(@RequestParam(required = false) ComplaintStatus status) {
        return handling.list(status).stream().map(d -> ComplaintView.of(d, zone)).toList();
    }

    @PostMapping("/api/v1/manager/complaints/{id}/start")
    ComplaintView start(@AuthenticationPrincipal SessionUser manager, @PathVariable long id) {
        return ComplaintView.of(handling.start(manager.id(), id), zone);
    }

    @PostMapping("/api/v1/manager/complaints/{id}/resolve")
    ComplaintView resolve(@AuthenticationPrincipal SessionUser manager, @PathVariable long id,
                          @Valid @RequestBody ResolveRequest body) {
        long refund = body.refundPoints() == null ? 0 : body.refundPoints();
        return ComplaintView.of(handling.resolve(manager.id(), id, body.note(), refund), zone);
    }

    @PostMapping("/api/v1/manager/complaints/{id}/reject")
    ComplaintView reject(@AuthenticationPrincipal SessionUser manager, @PathVariable long id,
                         @Valid @RequestBody NoteRequest body) {
        return ComplaintView.of(handling.reject(manager.id(), id, body.note()), zone);
    }

    @PostMapping("/api/v1/manager/complaints/{id}/propose")
    ComplaintView propose(@AuthenticationPrincipal SessionUser manager, @PathVariable long id,
                          @Valid @RequestBody ProposeRequest body) {
        return ComplaintView.of(handling.propose(manager.id(), id, body.points(), body.note()), zone);
    }

    @GetMapping("/api/v1/manager/complaints/{id}/photo")
    ResponseEntity<byte[]> photo(@PathVariable long id) {
        IncidentPhoto photo = handling.photo(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.noStore()).body(photo.data());
    }

    @PostMapping("/api/v1/manager/complaints/{id}/camera")
    ComplaintView updateCamera(@AuthenticationPrincipal SessionUser manager, @PathVariable long id,
                               @Valid @RequestBody CameraReviewRequest body) {
        return ComplaintView.of(handling.updateCameraReview(
                manager.id(), id, body.cameraStatus(), body.cameraNote(),
                body.photoContentType(), body.photoBase64()), zone);
    }

    @GetMapping("/api/v1/manager/complaints/{id}/camera-photo")
    ResponseEntity<byte[]> managerCameraPhoto(@PathVariable long id) {
        IncidentPhoto photo = handling.cameraPhoto(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.noStore()).body(photo.data());
    }

    // ===== Chủ bãi xe =====

    @GetMapping("/api/v1/owner/compensations")
    List<ComplaintView> compensations() {
        return decisions.list().stream().map(d -> ComplaintView.of(d, zone)).toList();
    }

    @GetMapping("/api/v1/owner/complaints/{id}/camera-photo")
    ResponseEntity<byte[]> ownerCameraPhoto(@PathVariable long id) {
        IncidentPhoto photo = decisions.cameraPhoto(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.noStore()).body(photo.data());
    }

    @PostMapping("/api/v1/owner/compensations/{id}/approve")
    DecisionResponse approve(@AuthenticationPrincipal SessionUser owner, @PathVariable long id,
                             @Valid @RequestBody NoteRequest body) {
        CompensationDecisionUseCase.Decision decision = decisions.approve(owner.id(), id, body.note());
        return new DecisionResponse(ComplaintView.of(decision.details(), zone), decision.replayed());
    }

    @PostMapping("/api/v1/owner/compensations/{id}/reject")
    ComplaintView rejectCompensation(@AuthenticationPrincipal SessionUser owner, @PathVariable long id,
                                     @Valid @RequestBody NoteRequest body) {
        return ComplaintView.of(decisions.reject(owner.id(), id, body.note()), zone);
    }
}
