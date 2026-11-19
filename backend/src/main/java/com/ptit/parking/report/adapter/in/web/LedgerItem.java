package com.ptit.parking.report.adapter.in.web;

import com.ptit.parking.report.application.port.out.LedgerQuery.Entry;
import com.ptit.parking.report.application.port.out.LedgerQuery.Party;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** Một dòng sổ giao dịch trả cho giao diện (dùng chung cho tab Giao dịch và hồ sơ sinh viên). */
record LedgerItem(long id, String type, long points, Long cashAmount, PartyView from, PartyView to,
                  String performerName, String performerRole, String createdAt, String plateNumber, String reason,
                  Long originalTransactionId) {

    record PartyView(String fullName, String studentCode, String role) {

        static PartyView of(Party party) {
            return party == null ? null : new PartyView(party.fullName(), party.studentCode(), party.roleCode());
        }
    }

    static LedgerItem of(Entry entry, ZoneId zone) {
        return new LedgerItem(entry.id(), entry.type().name(), entry.points(), entry.cashAmount(),
                PartyView.of(entry.from()), PartyView.of(entry.to()), entry.performerName(), entry.performerRole(),
                iso(entry.createdAt(), zone), entry.plateNumber(), entry.reason(), entry.originalTransactionId());
    }

    static String iso(Instant instant, ZoneId zone) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, zone).truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
