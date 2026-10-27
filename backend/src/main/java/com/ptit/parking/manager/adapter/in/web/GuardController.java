package com.ptit.parking.manager.adapter.in.web;

import com.ptit.parking.manager.application.usecase.CreateGuardUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Quản lý tài khoản bảo vệ (QL03, QL04). Quyền theo từng API trong ma trận ProtectedEndpoint. */
@RestController
@RequestMapping("/api/v1/manager/guards")
class GuardController {

    private final CreateGuardUseCase createGuard;

    GuardController(CreateGuardUseCase createGuard) {
        this.createGuard = createGuard;
    }

    /** API #21 — tạo tài khoản bảo vệ (quyền GUARD_CREATE). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    GuardResponse create(@Valid @RequestBody CreateGuardRequest body) {
        return GuardResponse.from(createGuard.create(new CreateGuardUseCase.Command(
                body.fullName(), body.phone(), body.username(), body.password())));
    }
}