package com.ptit.parking.manager.adapter.in.web;

import com.ptit.parking.manager.application.usecase.ChangeGuardStatusUseCase;
import com.ptit.parking.manager.application.usecase.CreateGuardUseCase;
import com.ptit.parking.manager.application.usecase.ListGuardsUseCase;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final ListGuardsUseCase listGuards;
    private final ChangeGuardStatusUseCase changeGuardStatus;

    GuardController(CreateGuardUseCase createGuard, ListGuardsUseCase listGuards,
                    ChangeGuardStatusUseCase changeGuardStatus) {
        this.createGuard = createGuard;
        this.listGuards = listGuards;
        this.changeGuardStatus = changeGuardStatus;
    }

    /** API #20 — danh sách bảo vệ (quyền GUARD_CREATE hoặc GUARD_LOCK). */
    @GetMapping
    List<GuardResponse> list() {
        return listGuards.list().stream().map(GuardResponse::from).toList();
    }

    /** API #21 — tạo tài khoản bảo vệ (quyền GUARD_CREATE). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    GuardResponse create(@Valid @RequestBody CreateGuardRequest body) {
        return GuardResponse.from(createGuard.create(new CreateGuardUseCase.Command(
                body.fullName(), body.phone(), body.username(), body.password())));
    }

    /** API #22 — khóa / mở khóa tài khoản bảo vệ (quyền GUARD_LOCK). */
    @PatchMapping("/{id}/status")
    GuardResponse changeStatus(@PathVariable long id, @Valid @RequestBody ChangeGuardStatusRequest body) {
        return GuardResponse.from(changeGuardStatus.change(
                new ChangeGuardStatusUseCase.Command(id, body.accountStatus())));
    }
}
