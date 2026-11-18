package com.ptit.parking.student.adapter.in.web;

import com.ptit.parking.shared.security.SessionUser;
import com.ptit.parking.student.application.usecase.ListMyVehiclesUseCase;
import com.ptit.parking.student.application.usecase.ManageMyVehicleUseCase;
import com.ptit.parking.student.application.usecase.RegisterVehicleUseCase;
import com.ptit.parking.student.domain.Vehicle;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API "xe của tôi" — quyền VEHICLE_REGISTER (ma trận ProtectedEndpoint). Chủ xe lấy từ PHIÊN
 * ({@link SessionUser}), không bao giờ từ request.
 */
@RestController
@RequestMapping("/api/v1/me/vehicles")
class MyVehicleController {

    /** Body của PATCH /me/vehicles/{id} (Đợt 1): chỉ sửa được loại xe — đổi biển số là đăng ký xe mới. */
    record ChangeTypeRequest(
            @NotBlank(message = "Vui lòng chọn loại xe")
            @Size(max = 20, message = "Mã loại xe tối đa 20 ký tự")
            String vehicleTypeCode) {
    }

    private final RegisterVehicleUseCase registerVehicle;
    private final ListMyVehiclesUseCase listMyVehicles;
    private final ManageMyVehicleUseCase manageVehicle;
    private final ZoneId zone;

    MyVehicleController(RegisterVehicleUseCase registerVehicle, ListMyVehiclesUseCase listMyVehicles,
                        ManageMyVehicleUseCase manageVehicle, Clock clock) {
        this.registerVehicle = registerVehicle;
        this.listMyVehicles = listMyVehicles;
        this.manageVehicle = manageVehicle;
        this.zone = clock.getZone();
    }

    /** API #5 — danh sách xe của tôi. */
    @GetMapping
    List<VehicleResponse> list(@AuthenticationPrincipal SessionUser student) {
        return listMyVehicles.list(student.id()).stream()
                .map(vehicle -> VehicleResponse.from(vehicle, zone))
                .toList();
    }

    /** API #6 — đăng ký xe. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    VehicleResponse register(@AuthenticationPrincipal SessionUser student,
                             @Valid @RequestBody RegisterVehicleRequest body) {
        Vehicle vehicle = registerVehicle.register(new RegisterVehicleUseCase.Command(
                student.id(), body.plateNumber(), body.vehicleTypeCode()));
        return VehicleResponse.from(vehicle, zone);
    }

    /** Đợt 1 — sửa loại xe. Xe của người khác → 404; xe đang trong bãi → 409 VEHICLE_PARKED. */
    @PatchMapping("/{vehicleId}")
    VehicleResponse changeType(@AuthenticationPrincipal SessionUser student, @PathVariable long vehicleId,
                               @Valid @RequestBody ChangeTypeRequest body) {
        return VehicleResponse.from(manageVehicle.changeType(new ManageMyVehicleUseCase.ChangeTypeCommand(
                student.id(), vehicleId, body.vehicleTypeCode())), zone);
    }

    /** Đợt 1 — ngừng sử dụng xe ("xóa xe"): giữ lại lịch sử lượt gửi, biển số được giải phóng. */
    @PostMapping("/{vehicleId}/deactivate")
    VehicleResponse deactivate(@AuthenticationPrincipal SessionUser student, @PathVariable long vehicleId) {
        return VehicleResponse.from(manageVehicle.deactivate(new ManageMyVehicleUseCase.DeactivateCommand(
                student.id(), vehicleId)), zone);
    }
}