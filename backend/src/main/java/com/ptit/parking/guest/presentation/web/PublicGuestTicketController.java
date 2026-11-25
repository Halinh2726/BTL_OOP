package com.ptit.parking.guest.presentation.web;

import com.ptit.parking.guest.application.GuestParking;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PublicGuestTicketController {
    private final GuestParking guest;

    PublicGuestTicketController(GuestParking guest) {
        this.guest = guest;
    }

    @GetMapping("/api/v1/public/guest-tickets/{token}")
    ResponseEntity<GuestParking.PublicTicket> ticket(@PathVariable String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer").body(guest.publicTicket(token));
    }
}
