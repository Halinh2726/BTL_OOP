package com.ptit.parking.gate.adapter.in.web;

import com.ptit.parking.gate.adapter.in.web.GateResponses.ShiftView;
import com.ptit.parking.gate.application.usecase.ShiftAttendanceUseCase;
import com.ptit.parking.gate.application.usecase.ShiftAttendanceUseCase.GuardTotals;
import jakarta.validation.constraints.Positive;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Chấm công bảo vệ cho quản lý (quyền GUARD_CREATE / GUARD_LOCK): bảng tổng hợp theo bảo vệ + chi tiết từng ca
 * trong khoảng ngày (giờ Việt Nam, tối đa 93 ngày). Chỉ đọc.
 */
@RestController
@RequestMapping("/api/v1/manager/guard-shifts")
class GuardTimesheetController {

    record TimesheetResponse(String from, String to, List<GuardTotals> totals, List<ShiftView> shifts) {}

    private final ShiftAttendanceUseCase attendance;
    private final ZoneId zone;

    GuardTimesheetController(ShiftAttendanceUseCase attendance, Clock clock) {
        this.attendance = attendance;
        this.zone = clock.getZone();
    }

    @GetMapping
    TimesheetResponse timesheet(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                @RequestParam(required = false) @Positive(message = "guardId phải > 0") Long guardId) {
        ShiftAttendanceUseCase.Timesheet sheet = attendance.timesheet(from, to, guardId);
        return new TimesheetResponse(from.toString(), to.toString(), sheet.totals(),
                sheet.shifts().stream().map(shift -> ShiftView.of(shift, sheet.names(), zone, sheet.now())).toList());
    }
}
