package com.ptit.parking.guest.presentation.web;

import com.ptit.parking.guest.application.GuestParking;
import com.ptit.parking.guest.application.GuestParkingStore.ListedSession;
import com.ptit.parking.guest.application.GuestParkingStore.Revenue;
import com.ptit.parking.guest.application.GuestParkingStore.Settings;
import com.ptit.parking.shared.infrastructure.security.SessionUser;
import com.ptit.parking.shared.presentation.web.CsvExport;
import com.ptit.parking.shared.presentation.web.TabularReport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class GuestManagementController {
    record ConfigRequest(@NotNull Boolean enabled, @NotNull @Min(0) @Max(10_000_000) Integer lostTicketFeeVnd) {}

    record SessionReport(List<ListedSession> items, long total, Revenue revenue) {}

    private final GuestParking guest;
    private final ZoneId zone;

    GuestManagementController(GuestParking guest, Clock clock) {
        this.guest = guest;
        this.zone = clock.getZone();
    }

    @GetMapping("/api/v1/manager/guest-config")
    Settings config() {
        return guest.settings();
    }

    @PutMapping("/api/v1/manager/guest-config")
    Settings update(@Valid @RequestBody ConfigRequest body) {
        return guest.updateSettings(new Settings(body.enabled(), body.lostTicketFeeVnd()));
    }

    @GetMapping("/api/v1/manager/guest-sessions")
    SessionReport sessions(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           @RequestParam(defaultValue = "") String status, @RequestParam(defaultValue = "") String plate,
                           @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
                           @RequestParam(defaultValue = "0") @Min(0) int offset) {
        var page = guest.search(from, to, status, plate, limit, offset);
        return new SessionReport(page.items(), page.total(), guest.revenue(from, to));
    }

    @PostMapping("/api/v1/manager/guest-sessions/{id}/approve-lost-ticket")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void approve(@PathVariable @Positive long id, @AuthenticationPrincipal SessionUser manager) {
        guest.approveLostTicket(id, manager.id());
    }

    @GetMapping("/api/v1/manager/guest-sessions/export")
    ResponseEntity<byte[]> export(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  @RequestParam(defaultValue = "") String status,
                                  @RequestParam(defaultValue = "") String plate) {
        List<List<Object>> rows = new ArrayList<>();
        int offset = 0;
        while (true) {
            var page = guest.search(from, to, status, plate, 500, offset);
            for (ListedSession item : page.items()) {
                String method = item.paymentMethod() == null ? "" : switch (item.paymentMethod()) {
                    case TIEN_MAT -> "Tiền mặt";
                    case VIETQR -> "VietQR";
                };
                rows.add(Arrays.asList(item.ticketCode(), item.plateDisplay(), item.vehicleTypeCode(),
                        item.enteredAt().atZone(zone).toOffsetDateTime(),
                        item.exitedAt() == null ? null : item.exitedAt().atZone(zone).toOffsetDateTime(),
                        item.lostTicket() ? "Có" : "", method, item.amount()));
            }
            offset += page.items().size();
            if (offset >= page.total() || page.items().isEmpty()) {
                break;
            }
        }
        return CsvExport.response(new GuestCsv("luot-xe-khach_" + from + "_" + to, rows));
    }

    @GetMapping("/api/v1/owner/guest-revenue")
    Revenue revenue(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return guest.revenue(from, to);
    }

    private record GuestCsv(String fileName, List<List<Object>> rows) implements TabularReport {
        @Override
        public List<String> headers() {
            return List.of("Mã vé", "Biển số", "Loại xe", "Giờ vào", "Giờ ra", "Mất vé", "Thanh toán", "Thực thu (đ)");
        }
    }
}
