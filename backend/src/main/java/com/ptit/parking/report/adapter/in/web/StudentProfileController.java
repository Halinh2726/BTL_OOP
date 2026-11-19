package com.ptit.parking.report.adapter.in.web;

import com.ptit.parking.report.application.port.out.StudentProfileQuery.StudentRow;
import com.ptit.parking.report.application.usecase.StudentProfileUseCase;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Đợt 4a — hồ sơ sinh viên cho quản lý (quyền STUDENT_VIEW). Chỉ đọc. */
@RestController
@RequestMapping("/api/v1/manager/student-profiles")
class StudentProfileController {

    record StudentView(long userId, String studentCode, String fullName, String email, String phone, String status,
                       String createdAt, long balance) {}

    record VehicleView(long id, String plateNumber, String vehicleTypeName, String status, String registeredAt,
                       boolean parkedNow) {}

    record SessionView(long id, String plateNumber, String enteredAt, String exitedAt, Long fee) {}

    record ProfileResponse(StudentView student, List<VehicleView> vehicles, List<SessionView> recentSessions,
                           List<LedgerItem> recentTransactions) {}

    private final StudentProfileUseCase profiles;
    private final ZoneId zone;

    StudentProfileController(StudentProfileUseCase profiles, Clock clock) {
        this.profiles = profiles;
        this.zone = clock.getZone();
    }

    @GetMapping
    List<StudentRow> search(@RequestParam("q") String query) {
        return profiles.search(query);
    }

    @GetMapping("/{studentCode}")
    ProfileResponse profile(@PathVariable String studentCode) {
        StudentProfileUseCase.Profile p = profiles.profile(studentCode);
        var s = p.student();
        return new ProfileResponse(
                new StudentView(s.userId(), s.studentCode(), s.fullName(), s.email(), s.phone(), s.status(),
                        LedgerItem.iso(s.createdAt(), zone), s.balance()),
                p.vehicles().stream().map(v -> new VehicleView(v.id(), v.plateNumber(), v.vehicleTypeName(), v.status(),
                        LedgerItem.iso(v.registeredAt(), zone), v.parkedNow())).toList(),
                p.recentSessions().stream().map(r -> new SessionView(r.id(), r.plateNumber(),
                        LedgerItem.iso(r.enteredAt(), zone), LedgerItem.iso(r.exitedAt(), zone), r.fee())).toList(),
                p.recentTransactions().stream().map(entry -> LedgerItem.of(entry, zone)).toList());
    }
}
