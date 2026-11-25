package com.ptit.parking.guest.domain;

import com.ptit.parking.student.domain.PlateNumber;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

public record GuestSession(
        long id, String ticketCode, String ticketToken, PlateNumber plate, String vehicleTypeCode,
        Instant enteredAt, Instant exitedAt, boolean lostTicket, boolean lostTicketApproved
) {
    public static final String QR_PREFIX = "PTIT-GUEST:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter TICKET_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    public static String newToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static String ticketCode(LocalDate date, int number) {
        return "K-" + TICKET_DATE.format(date) + "-" + String.format(java.util.Locale.ROOT, "%04d", number);
    }

    public boolean isOpen() {
        return exitedAt == null;
    }
}
