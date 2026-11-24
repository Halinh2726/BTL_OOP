package com.ptit.parking.gate.presentation.web;

import com.ptit.parking.gate.presentation.web.GateResponses.ActivityItem;
import com.ptit.parking.gate.presentation.web.GateResponses.IncidentItem;
import com.ptit.parking.gate.presentation.web.GateResponses.ParkedVehicleItem;
import com.ptit.parking.gate.presentation.web.GateResponses.ShiftView;
import com.ptit.parking.gate.presentation.web.GateResponses.SummaryView;
import com.ptit.parking.gate.application.usecase.ReportIncidentUseCase;
import com.ptit.parking.gate.application.usecase.ShiftAttendanceUseCase;
import com.ptit.parking.gate.application.usecase.ShiftHandoverUseCase;
import com.ptit.parking.gate.application.usecase.ViewMyActivityUseCase;
import com.ptit.parking.gate.application.usecase.ViewParkingLotUseCase;
import com.ptit.parking.gate.domain.CashOnHand;
import com.ptit.parking.gate.domain.GuardShift;
import com.ptit.parking.gate.domain.IncidentPhoto;
import com.ptit.parking.gate.domain.IncidentType;
import com.ptit.parking.shared.infrastructure.security.SessionUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Đợt 2 — màn hình bảo vệ ngoài xe vào/ra: xe trong bãi, lịch sử ca, nhận/bàn giao ca, báo sự cố.
 * Quyền theo ma trận ProtectedEndpoint (PARKING_CHECK_IN/OUT, INCIDENT_REPORT). Bảo vệ luôn lấy từ phiên.
 */
@RestController
@RequestMapping("/api/v1/guard")
class GateController {

    record LotResponse(long total, long overdue, List<ParkedVehicleItem> items) {}

    record ActivityResponse(String from, boolean inShift, long checkIns, long checkOuts, List<ActivityItem> items) {}

    record ShiftOverviewResponse(ShiftView current, SummaryView liveSummary, List<ShiftView> pendingHandovers,
                                 List<IncidentItem> openIncidents) {}

    /** {@code openingCash}: số tiền mặt (đồng) bảo vệ đang cầm lúc nhận ca. */
    record StartShiftRequest(
            Set<Long> acknowledgedHandoverIds,

            @NotNull(message = "Vui lòng nhập số tiền mặt đang cầm (không có thì nhập 0)")
            @PositiveOrZero(message = "Số tiền không được âm")
            @Max(value = CashOnHand.MAX, message = "Tối đa 50.000.000 đ")
            Long openingCash) {
    }

    /** {@code closingCash}: số tiền mặt (đồng) bảo vệ bàn giao lại cho ca sau. */
    record EndShiftRequest(
            @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
            String note,

            @NotNull(message = "Vui lòng nhập số tiền mặt bàn giao (không có thì nhập 0)")
            @PositiveOrZero(message = "Số tiền không được âm")
            @Max(value = CashOnHand.MAX, message = "Tối đa 50.000.000 đ")
            Long closingCash) {
    }

    /** Ảnh gửi dạng Base64 (frontend đã thu nhỏ ảnh trước khi gửi). */
    record ReportIncidentRequest(
            @NotNull(message = "Vui lòng chọn loại sự cố")
            IncidentType type,

            @Size(max = 20, message = "Biển số tối đa 20 ký tự")
            String plateNumber,

            @NotBlank(message = "Vui lòng mô tả sự cố")
            @Size(max = 1000, message = "Mô tả tối đa 1000 ký tự")
            String description,

            @Pattern(regexp = "^image/(jpeg|png|webp)$", message = "Ảnh phải là JPG, PNG hoặc WEBP")
            String photoContentType,

            @Size(max = 3_000_000, message = "Ảnh quá lớn (tối đa 2 MB)")
            String photoBase64) {
    }

    private final ViewParkingLotUseCase parkingLot;
    private final ViewMyActivityUseCase myActivity;
    private final ShiftHandoverUseCase shifts;
    private final ReportIncidentUseCase incidents;
    private final ShiftAttendanceUseCase attendance;
    private final Clock clock;
    private final ZoneId zone;

    GateController(ViewParkingLotUseCase parkingLot, ViewMyActivityUseCase myActivity, ShiftHandoverUseCase shifts,
                   ReportIncidentUseCase incidents, ShiftAttendanceUseCase attendance, Clock clock) {
        this.parkingLot = parkingLot;
        this.myActivity = myActivity;
        this.shifts = shifts;
        this.incidents = incidents;
        this.attendance = attendance;
        this.clock = clock;
        this.zone = clock.getZone();
    }

    @GetMapping("/parked-vehicles")
    LotResponse parkedVehicles(@RequestParam(required = false) String q) {
        ViewParkingLotUseCase.LotView view = parkingLot.view(q);
        return new LotResponse(view.total(), view.overdue(), view.vehicles().stream()
                .map(vehicle -> ParkedVehicleItem.of(vehicle, view.now(), zone)).toList());
    }

    @GetMapping("/my-activity")
    ActivityResponse myActivity(@AuthenticationPrincipal SessionUser guard) {
        ViewMyActivityUseCase.MyActivity activity = myActivity.of(guard.id());
        return new ActivityResponse(GateResponses.time(activity.from(), zone), activity.inShift(),
                activity.checkIns(), activity.checkOuts(),
                activity.entries().stream().map(entry -> ActivityItem.of(entry, zone)).toList());
    }

    @GetMapping("/shifts/current")
    ShiftOverviewResponse currentShift(@AuthenticationPrincipal SessionUser guard) {
        ShiftHandoverUseCase.Overview overview = shifts.overview(guard.id());
        Map<Long, String> names = overview.names();
        Instant now = clock.instant();
        return new ShiftOverviewResponse(
                overview.current() == null ? null : ShiftView.of(overview.current(), names, zone, now),
                SummaryView.of(overview.liveSummary()),
                overview.pendingHandovers().stream().map(shift -> ShiftView.of(shift, names, zone, now)).toList(),
                overview.openIncidents().stream().map(incident -> IncidentItem.of(incident, names, zone)).toList());
    }

    @PostMapping("/shifts/start")
    ShiftView startShift(@AuthenticationPrincipal SessionUser guard, @Valid @RequestBody StartShiftRequest body) {
        Set<Long> acknowledged = body.acknowledgedHandoverIds() == null ? Set.of() : body.acknowledgedHandoverIds();
        GuardShift shift = shifts.start(guard.id(), acknowledged, new CashOnHand(body.openingCash()));
        return ShiftView.of(shift, Map.of(), zone, clock.instant());
    }

    @PostMapping("/shifts/end")
    ShiftView endShift(@AuthenticationPrincipal SessionUser guard, @Valid @RequestBody EndShiftRequest body) {
        GuardShift shift = shifts.end(guard.id(), new CashOnHand(body.closingCash()), body.note());
        return ShiftView.of(shift, Map.of(), zone, clock.instant());
    }

    /** Lịch sử trực ca của tôi (chấm công cá nhân), mới nhất trước. */
    @GetMapping("/shifts/history")
    List<ShiftView> myShiftHistory(@AuthenticationPrincipal SessionUser guard) {
        ShiftAttendanceUseCase.MyHistory history = attendance.myHistory(guard.id());
        return history.shifts().stream().map(shift -> ShiftView.of(shift, history.names(), zone, history.now())).toList();
    }

    @PostMapping("/incidents")
    @ResponseStatus(HttpStatus.CREATED)
    IncidentItem report(@AuthenticationPrincipal SessionUser guard, @Valid @RequestBody ReportIncidentRequest body) {
        return IncidentItem.of(incidents.report(new ReportIncidentUseCase.Command(guard.id(), body.type(),
                body.plateNumber(), body.description(), body.photoContentType(), body.photoBase64())), Map.of(), zone);
    }

    @GetMapping("/incidents")
    List<IncidentItem> myIncidents(@AuthenticationPrincipal SessionUser guard) {
        return incidents.mine(guard.id()).stream().map(incident -> IncidentItem.of(incident, Map.of(), zone)).toList();
    }

    /** Ảnh trả về dạng nhị phân; không cho trình duyệt lưu đệm vì là dữ liệu nghiệp vụ. */
    @GetMapping("/incidents/{incidentId}/photo")
    ResponseEntity<byte[]> photo(@AuthenticationPrincipal SessionUser guard, @PathVariable long incidentId) {
        IncidentPhoto photo = incidents.photo(guard.id(), incidentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.noStore())
                .body(photo.data());
    }
}
