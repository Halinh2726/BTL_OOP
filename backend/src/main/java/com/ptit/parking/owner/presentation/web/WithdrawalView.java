package com.ptit.parking.owner.presentation.web;

import com.ptit.parking.owner.domain.WithdrawalRequest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/** Một yêu cầu rút điểm trả cho giao diện (chủ bãi xe và quản lý dùng chung). Thời gian theo giờ Việt Nam. */
record WithdrawalView(long id, long points, int cashPerPoint, long cashAmount, String method, String status,
                      String createdAt, String cancelledAt, String processedByName, String processedAt,
                      String rejectReason, Long transactionId, String confirmedAt, String complaintNote,
                      String complainedAt, String resolutionNote, String resolvedByName, String resolvedAt) {

    static WithdrawalView of(WithdrawalRequest r, Map<Long, String> names, ZoneId zone) {
        return new WithdrawalView(r.id(), r.points(), r.rate().cashPerPoint(), r.cashAmount(), "TIEN_MAT",
                r.status().name(), time(r.createdAt(), zone), time(r.cancelledAt(), zone),
                r.processedBy() == null ? null : names.get(r.processedBy()), time(r.processedAt(), zone),
                r.rejectReason(), r.transactionId(), time(r.confirmedAt(), zone), r.complaintNote(),
                time(r.complainedAt(), zone), r.resolutionNote(),
                r.resolvedBy() == null ? null : names.get(r.resolvedBy()), time(r.resolvedAt(), zone));
    }

    static String time(Instant instant, ZoneId zone) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, zone).truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
