package com.ptit.parking.parking.adapter.in.web;

import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.VehicleOwner;
import com.ptit.parking.parking.application.usecase.CheckOutUseCase;
import com.ptit.parking.parking.application.usecase.PreviewCheckOutUseCase;
import com.ptit.parking.parking.application.usecase.PreviewCheckOutUseCase.Preview;
import com.ptit.parking.parking.domain.ParkingSession;
import com.ptit.parking.payment.domain.ParkingPayment;
import com.ptit.parking.shared.security.SessionUser;
import com.ptit.parking.shared.web.IdempotencyKeyHeader;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
                    session == null ? null : format(session.enteredAt(), zone),
                    preview.balance(), preview.fee(), preview.allowed(),
                    preview.reason() == null ? null : preview.reason().name());
        }
    }

    /** Body của API #15: chỉ có lượt gửi. Phí và ví nhận KHÔNG bao giờ nhận từ request (R06). */
    record CheckOutRequest(
            @NotNull(message = "Vui lòng chọn lượt gửi")
            @Positive(message = "Mã lượt gửi không hợp lệ")
            Long sessionId) {
    }

    /** Response 201 của API #15. balanceAfter là số điểm còn lại của SINH VIÊN. */
    record CheckOutResponse(long transactionId, long sessionId, long fee, String exitedAt, long balanceAfter) {

        static CheckOutResponse from(ParkingPayment payment, ZoneId zone) {
            return new CheckOutResponse(payment.id(), payment.sessionId(), payment.points(),
                    format(payment.createdAt(), zone), payment.sourceBalanceAfter());
        }
    }

    private final PreviewCheckOutUseCase previewCheckOut;
    private final CheckOutUseCase checkOut;
    private final ZoneId zone;

    CheckOutController(PreviewCheckOutUseCase previewCheckOut, CheckOutUseCase checkOut, Clock clock) {
        this.previewCheckOut = previewCheckOut;
        this.checkOut = checkOut;
        this.zone = clock.getZone();
    }

    /** API #14 — tra cứu trước khi cho xe ra, kèm phí tạm tính. Chỉ đọc. */
    @GetMapping("/preview")
    PreviewResponse preview(@RequestParam String plate) {
        return PreviewResponse.from(previewCheckOut.preview(plate), zone);
    }

    /** API #15 — xác nhận cho xe ra và tự động thanh toán. Bảo vệ cho ra là người đang đăng nhập, lấy từ PHIÊN. */
    @PostMapping
    ResponseEntity<CheckOutResponse> checkOut(@AuthenticationPrincipal SessionUser guard,
                                              @RequestHeader(value = IdempotencyKeyHeader.NAME, required = false) String key,
                                              @Valid @RequestBody CheckOutRequest body) {
        CheckOutUseCase.Result result = checkOut.checkOut(new CheckOutUseCase.Command(
                guard.id(), IdempotencyKeyHeader.parse(key), body.sessionId()));
        return IdempotencyKeyHeader.created(CheckOutResponse.from(result.payment(), zone), result.replayed());
    }

    /** Giờ Việt Nam kèm múi giờ, ví dụ "2026-10-05T18:30:00+07:00" (CSDL lưu UTC). */
    private static String format(Instant instant, ZoneId zone) {
        return OffsetDateTime.ofInstant(instant, zone).truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
