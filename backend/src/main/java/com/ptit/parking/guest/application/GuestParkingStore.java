package com.ptit.parking.guest.application;

import com.ptit.parking.guest.domain.GuestPayment;
import com.ptit.parking.guest.domain.GuestSession;
import com.ptit.parking.student.domain.PlateNumber;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GuestParkingStore {
    record Settings(boolean enabled, int lostTicketFeeVnd) {}

    record Entry(PlateNumber plate, String vehicleTypeCode, String name, String phone, String note) {}

    record ListedSession(long id, String ticketCode, String plateNumber, String plateDisplay, String vehicleTypeCode,
                         String guestName, String guestPhone, String note, Instant enteredAt, Instant exitedAt,
                         boolean lostTicket, boolean lostTicketApproved, GuestPayment.Method paymentMethod, Long amount) {}

    record Search(Instant from, Instant until, String status, String plate, int limit, int offset, Instant overdueBefore) {}

    record Page(List<ListedSession> items, long total, long overdue) {}

    record Revenue(long payments, long cash, long vietqr) {}

    record VietQrQuote(UUID id, long feePoints, long amount, boolean overnight, Instant expiresAt) {}

    Settings settings();

    void saveSettings(Settings settings);

    int nextTicketNumber(LocalDate date);

    GuestSession enter(Entry entry, String ticketCode, String token, long guardId, Instant now);

    Optional<GuestSession> findOpenByPlate(PlateNumber plate);

    Optional<GuestSession> findByCode(String code);

    Optional<GuestSession> findByToken(String token);

    Optional<GuestSession> lockById(long id);

    void approveLostTicket(long id, long managerId);

    void close(long id, long guardId, Instant now, String documentLast4);

    Optional<GuestPayment> paymentByKey(UUID key);

    Optional<GuestPayment> paymentForSession(long sessionId);

    Optional<VietQrQuote> vietQrQuote(long sessionId);

    void saveVietQrQuote(long sessionId, VietQrQuote quote);

    void insertPayment(GuestPayment payment);

    Page search(Search search);

    Revenue revenue(Instant from, Instant until);

    void anonymizeBefore(Instant cutoff);
}
