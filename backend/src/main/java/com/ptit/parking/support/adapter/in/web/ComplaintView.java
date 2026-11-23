package com.ptit.parking.support.adapter.in.web;

import com.ptit.parking.support.application.port.out.ComplaintDirectory.SessionInfo;
import com.ptit.parking.support.application.usecase.ComplaintDetails;
import com.ptit.parking.support.domain.Complaint;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** Một khiếu nại trả cho giao diện — dùng chung cho sinh viên, quản lý, chủ bãi xe. */
record ComplaintView(
        long id,
        String type,
        String status,
        String description,
        String createdAt,
        boolean hasPhoto,
        String studentCode,
        String studentName,
        SessionView session,
        String handlerName,
        String handledAt,
        String resolution,
        Long refundTransactionId,
        Long proposedPoints,
        String proposerName,
        String proposedAt,
        String deciderName,
        String decidedAt,
        String decisionNote,
        Long compensationTransactionId,
        String cancelledAt,
        boolean cameraRequested,
        String cameraStatus,
        String incidentArea,
        String incidentTimeRange,
        String cameraNote,
        String cameraReviewerName,
        String cameraReviewedAt,
        boolean hasCameraPhoto) {

    record SessionView(long id, String plateNumber, String enteredAt, String exitedAt, Long fee) {}

    static ComplaintView of(ComplaintDetails d, ZoneId zone) {
        Complaint c = d.complaint();
        SessionInfo s = d.session();
        return new ComplaintView(
                c.id(),
                c.type().name(),
                c.status().name(),
                c.description(),
                time(c.createdAt(), zone),
                c.hasPhoto(),
                d.student() == null ? null : d.student().studentCode(),
                d.student() == null ? null : d.student().fullName(),
                s == null ? null : new SessionView(s.id(), s.plateNumber(), time(s.enteredAt(), zone),
                        time(s.exitedAt(), zone), s.fee()),
                d.handlerName(),
                time(c.handledAt(), zone),
                c.resolution(),
                c.refundTransactionId(),
                c.proposedPoints(),
                d.proposerName(),
                time(c.proposedAt(), zone),
                d.deciderName(),
                time(c.decidedAt(), zone),
                c.decisionNote(),
                c.compensationTransactionId(),
                time(c.cancelledAt(), zone),
                c.cameraRequested(),
                c.cameraStatus().name(),
                c.incidentArea(),
                c.incidentTimeRange(),
                c.cameraNote(),
                d.cameraReviewerName(),
                time(c.cameraReviewedAt(), zone),
                c.hasCameraPhoto());
    }

    static String time(Instant instant, ZoneId zone) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, zone).truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
