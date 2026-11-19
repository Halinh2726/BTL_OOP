package com.ptit.parking.gate.adapter.in.web;

import com.ptit.parking.gate.application.port.out.GateActivityQuery.ActivityEntry;
import com.ptit.parking.gate.domain.GuardShift;
import com.ptit.parking.gate.domain.Incident;
import com.ptit.parking.gate.domain.ParkedVehicle;
import com.ptit.parking.gate.domain.ShiftSummary;
import com.ptit.parking.student.domain.PlateNumber;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Các DTO trả về cho màn hình bảo vệ (Đợt 2). Thời gian theo giờ Việt Nam kèm múi giờ.
 * Không DTO nào có trường điểm hay tiền (R06).
 */
final class GateResponses {

    private GateResponses() {
    }

    record ParkedVehicleItem(long sessionId, String plateNumber, String plateDisplay, String vehicleTypeCode,
                             String studentCode, String fullName, String enteredAt, String entryGuardName,
                             long minutesParked, boolean overdue) {

        static ParkedVehicleItem of(ParkedVehicle vehicle, Instant now, ZoneId zone) {
            return new ParkedVehicleItem(vehicle.sessionId(), vehicle.plate().value(), vehicle.plate().display(),
                    vehicle.vehicleTypeCode(), vehicle.studentCode(), vehicle.fullName(),
                    time(vehicle.enteredAt(), zone), vehicle.entryGuardName(), vehicle.parkedFor(now).toMinutes(),
                    vehicle.isOverdue(now));
        }
    }

    record ActivityItem(long sessionId, String plateNumber, String plateDisplay, String vehicleTypeCode,
                        String studentCode, String fullName, String direction, String at) {

        static ActivityItem of(ActivityEntry entry, ZoneId zone) {
            return new ActivityItem(entry.sessionId(), entry.plate().value(), entry.plate().display(),
                    entry.vehicleTypeCode(), entry.studentCode(), entry.fullName(), entry.direction().name(),
                    time(entry.at(), zone));
        }
    }

    record SummaryView(int checkIns, int checkOuts, int vehiclesInLot, int openIncidents) {

        static SummaryView of(ShiftSummary summary) {
            return summary == null ? null : new SummaryView(summary.checkIns(), summary.checkOuts(),
                    summary.vehiclesInLot(), summary.openIncidents());
        }
    }

    /**
     * Một ca trực. Tiền mặt (đồng) là số bảo vệ TỰ KHAI khi nhận / bàn giao ca — không phải doanh thu (R06).
     * {@code openingDiscrepancy}: tiền khai nhận ca trừ tiền ca trước bàn giao (null nếu không nhận biên bản nào).
     */
    record ShiftView(long id, long guardId, String guardName, String startedAt, String endedAt, long workedMinutes,
                     SummaryView summary, String handoverNote, long openingCash, Long expectedOpeningCash,
                     Long openingDiscrepancy, Long closingCash, String receivedByName, String receivedAt) {

        static ShiftView of(GuardShift shift, Map<Long, String> names, ZoneId zone, Instant now) {
            return new ShiftView(shift.id(), shift.guardId(), names.get(shift.guardId()), time(shift.startedAt(), zone),
                    time(shift.endedAt(), zone), shift.worked(now).toMinutes(), SummaryView.of(shift.summary()),
                    shift.handoverNote(), shift.openingCash().amount(),
                    shift.expectedOpeningCash() == null ? null : shift.expectedOpeningCash().amount(),
                    shift.openingDiscrepancy().orElse(null),
                    shift.closingCash() == null ? null : shift.closingCash().amount(),
                    shift.receivedBy() == null ? null : names.get(shift.receivedBy()), time(shift.receivedAt(), zone));
        }
    }

    record IncidentItem(long id, String type, String plateNumber, String plateDisplay, String description,
                        String status, String createdAt, String reporterName, boolean hasPhoto, String resolution) {

        static IncidentItem of(Incident incident, Map<Long, String> names, ZoneId zone) {
            return new IncidentItem(incident.id(), incident.type().name(),
                    incident.plate().map(PlateNumber::value).orElse(null),
                    incident.plate().map(PlateNumber::display).orElse(null),
                    incident.description(), incident.status().name(), time(incident.createdAt(), zone),
                    names.get(incident.reporterId()), incident.hasPhoto(), incident.resolution());
        }
    }

    static String time(Instant instant, ZoneId zone) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, zone).truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
