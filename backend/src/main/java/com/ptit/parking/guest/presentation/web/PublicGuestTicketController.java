package com.ptit.parking.guest.presentation.web;

import com.ptit.parking.guest.application.GuestParking;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PublicGuestTicketController {
    record PublicTicketResponse(String enteredAt, String exitedAt, long minutesParked, long amount,
                                boolean overnight, boolean checkoutLocked, LocalTime checkoutCurfewTime) {}

    private final GuestParking guest;
    private final ZoneId zone;

    PublicGuestTicketController(GuestParking guest, Clock clock) {
        this.guest = guest;
        this.zone = clock.getZone();
    }

    @GetMapping("/api/v1/public/guest-tickets/{token}")
    ResponseEntity<PublicTicketResponse> ticket(@PathVariable String token) {
        GuestParking.PublicTicket ticket = guest.publicTicket(token);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer").body(new PublicTicketResponse(
                        GuestTimes.format(ticket.enteredAt(), zone), GuestTimes.format(ticket.exitedAt(), zone),
                        ticket.minutesParked(), ticket.amount(), ticket.overnight(), ticket.checkoutLocked(),
                        ticket.checkoutCurfewTime()));
    }
}
