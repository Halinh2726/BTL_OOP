package com.ptit.parking.student.adapter.in.web;

import com.ptit.parking.shared.security.SessionUser;
import com.ptit.parking.student.application.port.out.StudentSessionQuery;
import com.ptit.parking.student.application.port.out.StudentSessionQuery.SessionRecord;
import com.ptit.parking.student.domain.PlateNumber;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API xem xe đang gửi & lịch sử gửi xe của chính sinh viên (quyền VEHICLE_REGISTER). */
@RestController
@RequestMapping("/api/v1/me/sessions")
class MySessionsController {

    private static final int LIMIT = 30;

    record StudentSessionView(
            long id,
            String plateNumber,
            String plateDisplay,
            String vehicleTypeCode,
            String enteredAt,
            String exitedAt,
            Long fee,
            Long paymentTransactionId,
            boolean paidByPass,
            long minutesParked,
            String entryGuardName,
            String exitGuardName) {

        static StudentSessionView from(SessionRecord r, Instant now, ZoneId zone) {
            Instant end = r.exitedAt() != null ? r.exitedAt() : now;
            long minutes = Math.max(0L, Duration.between(r.enteredAt(), end).toMinutes());
            boolean paidByPass = r.exitedAt() != null && r.fee() == null && r.paymentTransactionId() == null;
            return new StudentSessionView(
                    r.id(),
                    r.plateNumber(),
                    new PlateNumber(r.plateNumber()).display(),
                    r.vehicleTypeCode(),
                    iso(r.enteredAt(), zone),
                    iso(r.exitedAt(), zone),
                    r.fee(),
                    r.paymentTransactionId(),
                    paidByPass,
                    minutes,
                    r.entryGuardName(),
                    r.exitGuardName());
        }
    }

    record StudentSessionsResponse(List<StudentSessionView> parked, List<StudentSessionView> recent) {}

    private final StudentSessionQuery query;
    private final Clock clock;

    MySessionsController(StudentSessionQuery query, Clock clock) {
        this.query = query;
        this.clock = clock;
    }

    @GetMapping
    StudentSessionsResponse list(@AuthenticationPrincipal SessionUser student) {
        Instant now = clock.instant();
        ZoneId zone = clock.getZone();
        List<SessionRecord> all = query.findByStudent(student.id(), LIMIT);
        List<StudentSessionView> parked = all.stream()
                .filter(SessionRecord::open)
                .map(r -> StudentSessionView.from(r, now, zone))
                .toList();
        List<StudentSessionView> recent = all.stream()
                .map(r -> StudentSessionView.from(r, now, zone))
                .toList();
        return new StudentSessionsResponse(parked, recent);
    }

    private static String iso(Instant instant, ZoneId zone) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, zone).truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
