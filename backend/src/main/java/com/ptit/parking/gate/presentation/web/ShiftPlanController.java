package com.ptit.parking.gate.presentation.web;

import com.ptit.parking.gate.application.port.out.GuardRoster.GuardInfo;
import com.ptit.parking.gate.application.usecase.ShiftPlanUseCase;
import com.ptit.parking.gate.application.usecase.ShiftPlanUseCase.Planned;
import com.ptit.parking.gate.domain.ShiftAssignment;
import com.ptit.parking.gate.domain.ShiftSlot;
import com.ptit.parking.shared.infrastructure.security.SessionUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Đợt 4b — phân ca (quyền SHIFT_PLAN) và lịch trực của bảo vệ. */
@RestController
class ShiftPlanController {

    record AssignRequest(@NotNull(message = "Vui lòng chọn ngày") LocalDate date,
                         @NotNull(message = "Vui lòng chọn ca") ShiftSlot slot,
                         @NotNull(message = "Vui lòng chọn bảo vệ") @Positive Long guardId) {}

    /** Một ca đã xếp: giờ ca, bảo vệ, kết quả đối chiếu với giờ nhận ca thực tế. */
    record PlannedView(long id, String date, String slot, String startsAt, String endsAt, long guardId,
                       String guardName, String attendance, String actualStart, long lateMinutes) {}

    record GuardOption(long id, String fullName, boolean active) {}

    record WeekView(String from, String to, List<GuardOption> guards, List<PlannedView> shifts) {}

    private final ShiftPlanUseCase plans;
    private final ZoneId zone;

    ShiftPlanController(ShiftPlanUseCase plans, Clock clock) {
        this.plans = plans;
        this.zone = clock.getZone();
    }

    @GetMapping("/api/v1/manager/shift-plan")
    WeekView week(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
        ShiftPlanUseCase.Week week = plans.week(from);
        return new WeekView(week.from().toString(), week.to().toString(),
                week.guards().stream().map(ShiftPlanController::option).toList(),
                week.shifts().stream().map(this::view).toList());
    }

    @PostMapping("/api/v1/manager/shift-plan")
    @ResponseStatus(HttpStatus.CREATED)
    PlannedView assign(@AuthenticationPrincipal SessionUser manager, @Valid @RequestBody AssignRequest body) {
        return view(plans.assign(manager.id(), body.date(), body.slot(), body.guardId()));
    }

    @DeleteMapping("/api/v1/manager/shift-plan/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable long id) {
        plans.remove(id);
    }

    @GetMapping("/api/v1/guard/my-schedule")
    List<PlannedView> mySchedule(@AuthenticationPrincipal SessionUser guard) {
        return plans.mySchedule(guard.id()).stream().map(this::view).toList();
    }

    private PlannedView view(Planned planned) {
        ShiftAssignment a = planned.assignment();
        return new PlannedView(a.id(), a.workDate().toString(), a.slot().name(),
                GateResponses.time(a.startsAt(zone), zone), GateResponses.time(a.endsAt(zone), zone), a.guardId(),
                planned.guardName(), planned.attendance().status().name(),
                GateResponses.time(planned.attendance().actualStart(), zone), planned.attendance().lateMinutes());
    }

    private static GuardOption option(GuardInfo guard) {
        return new GuardOption(guard.id(), guard.fullName(), guard.active());
    }
}
