package com.ptit.parking.student.adapter.in.web;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ptit.parking.shared.security.SessionUser;
import com.ptit.parking.student.application.usecase.RegisterVehicleUseCase;
import com.ptit.parking.student.domain.Vehicle;

import jakarta.validation.Valid;

/**
 * API "xe của tôi" — quyền VEHICLE_REGISTER (ma trận ProtectedEndpoint). Chủ xe lấy từ PHIÊN
 * ({@link SessionUser}), không bao giờ từ request.
 */
@RestController
@RequestMapping("/api/v1/me/vehicles")
class MyVehicleController {

    private final RegisterVehicleUseCase registerVehicle;
    private final ZoneId zone;

    MyVehicleController(RegisterVehicleUseCase registerVehicle, Clock clock) {
        this.registerVehicle = registerVehicle;
        this.zone = clock.getZone();
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
}