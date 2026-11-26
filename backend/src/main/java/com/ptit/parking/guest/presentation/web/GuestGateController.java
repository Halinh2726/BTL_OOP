package com.ptit.parking.guest.presentation.web;

import com.ptit.parking.guest.application.GuestParking;
import com.ptit.parking.guest.application.GuestParking.CheckoutPreview;
import com.ptit.parking.guest.application.GuestParking.EntryPreview;
import com.ptit.parking.guest.application.GuestParking.Lookup;
import com.ptit.parking.guest.application.GuestParking.Quote;
import com.ptit.parking.guest.application.GuestParking.Receipt;
import com.ptit.parking.guest.application.GuestParkingStore.Entry;
import com.ptit.parking.guest.domain.GuestPayment.Method;
import com.ptit.parking.guest.domain.GuestSession;
import com.ptit.parking.parking.domain.VietQrOrder;
import com.ptit.parking.shared.infrastructure.security.SessionUser;
import com.ptit.parking.shared.presentation.web.IdempotencyKeyHeader;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/gate/guest")
class GuestGateController {
    record EntryRequest(@Size(max = 32) String plateNumber, @NotBlank @Size(max = 20) String vehicleTypeCode,
                        @Size(max = 100) String guestName, @Size(max = 15) @Pattern(regexp = "\\+?[0-9]{6,15}") String guestPhone,
                        @Size(max = 255) String note) {
        Entry toEntry() {
            return new Entry(GuestParking.parsePlate(plateNumber), vehicleTypeCode,
                    trim(guestName), trim(guestPhone), trim(note));
        }

        private static String trim(String text) {
            return text == null || text.isBlank() ? null : text.strip();
        }
    }

    record LookupRequest(@Size(max = 32) String ticketToken, @Size(max = 20) String ticketCode,
                         @Size(max = 32) String plateNumber) {
        Lookup toLookup() {
            return new Lookup(ticketToken, ticketCode, plateNumber);
        }
    }

    record CheckoutRequest(@Positive long sessionId, @Valid LookupRequest ticket,
                           @Size(max = 32) String observedPlate, @Pattern(regexp = "[0-9]{4}") String documentLast4,
                           @Positive long expectedAmount, UUID quoteId) {
        GuestParking.Checkout toCheckout() {
            return new GuestParking.Checkout(sessionId, ticket == null ? null : ticket.toLookup(),
                    observedPlate, documentLast4, expectedAmount, quoteId);
        }
    }

    record VietQrRequest(@Valid LookupRequest ticket, @Size(max = 32) String observedPlate) {}

    record TicketResponse(long sessionId, String ticketCode, String ticketToken, String qrPayload,
                          String plateDisplay, String vehicleTypeCode, String enteredAt) {}

    record CheckoutResponse(long sessionId, String plateNumber, String plateDisplay, String vehicleTypeCode,
                            String enteredAt, Quote quote, int lostTicketFeeVnd, boolean lostTicketApproved) {}

    record PaymentResponse(String ticketCode, Method method, long amount, long lostTicketFee,
                           boolean overnight, boolean lostTicket, String paidAt) {}

    record ParkedGuest(long sessionId, String plateNumber, String plateDisplay, String vehicleTypeCode,
                       String enteredAt) {}

    record ParkedResponse(List<ParkedGuest> items, long total, long overdue) {}

    record VietQrResponse(UUID quoteId, String expiresAt, VietQrOrder order) {}

    private final GuestParking guest;
    private final ZoneId zone;

    GuestGateController(GuestParking guest, Clock clock) {
        this.guest = guest;
        this.zone = clock.getZone();
    }

    @PostMapping("/check-in/preview")
    EntryPreview previewEntry(@Valid @RequestBody EntryRequest body) {
        return guest.previewEntry(body.toEntry());
    }

    @PostMapping("/check-in")
    ResponseEntity<TicketResponse> checkIn(@AuthenticationPrincipal SessionUser guard,
                                          @Valid @RequestBody EntryRequest body) {
        GuestSession session = guest.checkIn(guard.id(), body.toEntry());
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(new TicketResponse(
                session.id(), session.ticketCode(), session.ticketToken(), GuestSession.QR_PREFIX + session.ticketToken(),
                session.plate() == null ? null : session.plate().display(), session.vehicleTypeCode(),
                GuestTimes.format(session.enteredAt(), zone)));
    }

    @PostMapping("/check-out/preview")
    CheckoutResponse previewCheckout(@Valid @RequestBody LookupRequest lookup) {
        CheckoutPreview preview = guest.previewCheckout(lookup.toLookup());
        GuestSession session = preview.session();
        return new CheckoutResponse(session.id(), session.plate() == null ? null : session.plate().value(),
                session.plate() == null ? null : session.plate().display(), session.vehicleTypeCode(),
                GuestTimes.format(session.enteredAt(), zone),
                preview.quote(), preview.lostTicketFeeVnd(), session.lostTicketApproved());
    }

    @PostMapping("/sessions/{id}/vietqr")
    ResponseEntity<VietQrResponse> vietQr(@PathVariable @Positive long id,
                                          @Valid @RequestBody VietQrRequest body) {
        GuestParking.VietQrPayment payment = guest.vietQr(id,
                body.ticket() == null ? null : body.ticket().toLookup(), body.observedPlate());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new VietQrResponse(
                payment.quoteId(), GuestTimes.format(payment.expiresAt(), zone), payment.order()));
    }

    @PostMapping("/check-out/cash")
    ResponseEntity<PaymentResponse> cash(@AuthenticationPrincipal SessionUser guard,
                                         @RequestHeader(name = IdempotencyKeyHeader.NAME, required = false) String key,
                                         @Valid @RequestBody CheckoutRequest body) {
        return pay(guard, key, body, Method.TIEN_MAT, false);
    }

    @PostMapping("/check-out/vietqr")
    ResponseEntity<PaymentResponse> vietQr(@AuthenticationPrincipal SessionUser guard,
                                           @RequestHeader(name = IdempotencyKeyHeader.NAME, required = false) String key,
                                           @Valid @RequestBody CheckoutRequest body) {
        return pay(guard, key, body, Method.VIETQR, false);
    }

    @PostMapping("/check-out/lost-ticket")
    ResponseEntity<PaymentResponse> lost(@AuthenticationPrincipal SessionUser guard,
                                         @RequestHeader(name = IdempotencyKeyHeader.NAME, required = false) String key,
                                         @Valid @RequestBody CheckoutRequest body) {
        return pay(guard, key, body, Method.TIEN_MAT, true);
    }

    @GetMapping("/parked")
    ParkedResponse parked(@RequestParam(defaultValue = "") String plate,
                          @RequestParam(defaultValue = "100") @Min(1) @Max(200) int limit,
                          @RequestParam(defaultValue = "0") @Min(0) int offset) {
        var page = guest.parked(plate, limit, offset);
        return new ParkedResponse(page.items().stream().map(s -> new ParkedGuest(
                s.id(), s.plateNumber(), s.plateDisplay(), s.vehicleTypeCode(),
                GuestTimes.format(s.enteredAt(), zone))).toList(), page.total(), page.overdue());
    }

    private ResponseEntity<PaymentResponse> pay(SessionUser guard, String key, CheckoutRequest body,
                                                Method method, boolean lost) {
        Receipt receipt = guest.checkout(guard.id(), IdempotencyKeyHeader.parse(key), method, lost, body.toCheckout());
        var payment = receipt.payment();
        return IdempotencyKeyHeader.created(new PaymentResponse(receipt.ticketCode(), payment.method(), payment.amount(),
                payment.lostTicketFee(), payment.overnight(), receipt.lostTicket(),
                GuestTimes.format(payment.paidAt(), zone)), receipt.replayed());
    }
}
