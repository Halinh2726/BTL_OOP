package com.ptit.parking.parking.adapter.in.web;

import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.VehicleOwner;
import com.ptit.parking.parking.application.usecase.PreviewCheckOutUseCase;
import com.ptit.parking.parking.application.usecase.PreviewCheckOutUseCase.Preview;
import com.ptit.parking.parking.domain.ParkingSession;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** BV02 — bảo vệ xác minh xe ra. Quyền PARKING_CHECK_OUT (ma trận ProtectedEndpoint). */
@RestController
@RequestMapping("/api/v1/guard/check-out")
class CheckOutController {

    /** Response của API #14. Trường nào không có (chưa đăng ký, không có lượt mở) thì null. */
    record PreviewResponse(Long sessionId, String plateNumber, String vehicleTypeCode, String studentCode,
                           String fullName, String enteredAt, Long balance, Long fee, boolean allowed, String reason) {

        static PreviewResponse from(Preview preview, ZoneId zone) {
            VehicleOwner owner = preview.owner();
            ParkingSession session = preview.session();
            return new PreviewResponse(
                    session == null ? null : session.id(),
                    owner == null ? null : owner.plate().value(),
                    owner == null ? null : owner.vehicleTypeCode(),
                    owner == null ? null : owner.studentCode(),
                    owner == null ? null : owner.fullName(),
                    session == null ? null : OffsetDateTime.ofInstant(session.enteredAt(), zone)
                            .truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    preview.balance(), preview.fee(), preview.allowed(),
                    preview.reason() == null ? null : preview.reason().name());
        }
    }

    private final PreviewCheckOutUseCase previewCheckOut;
    private final ZoneId zone;

    CheckOutController(PreviewCheckOutUseCase previewCheckOut, Clock clock) {
        this.previewCheckOut = previewCheckOut;
        this.zone = clock.getZone();
    }

    /** API #14 — tra cứu trước khi cho xe ra, kèm phí tạm tính. Chỉ đọc. */
    @GetMapping("/preview")
    PreviewResponse preview(@RequestParam String plate) {
        return PreviewResponse.from(previewCheckOut.preview(plate), zone);
    }
}