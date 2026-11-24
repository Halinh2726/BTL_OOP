package com.ptit.parking.parking.presentation.web;

import com.ptit.parking.parking.application.usecase.CheckOutByCashUseCase;
import com.ptit.parking.parking.application.usecase.PayParkingFeeByVietQrUseCase;
import com.ptit.parking.parking.domain.GateCashCollection;
import com.ptit.parking.payment.domain.ParkingPayment;
import com.ptit.parking.shared.infrastructure.security.SessionUser;
import com.ptit.parking.shared.presentation.web.IdempotencyKeyHeader;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Các phương thức thanh toán bổ sung khi bảo vệ cho xe ra (quyền PARKING_CHECK_OUT):
 * <ul>
 *   <li>{@code POST /api/v1/guard/check-out/vietqr}: xác nhận chuyển khoản VietQR tại cổng.</li>
 *   <li>{@code POST /api/v1/guard/check-out/cash}: thu tiền mặt tại chốt (ghi vào bảng kê ca, không vào ví điểm).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/guard/check-out")
class GatePaymentController {

    record SessionRequest(
            @NotNull(message = "Vui lòng chọn lượt gửi")
            @Positive(message = "Mã lượt gửi không hợp lệ")
            Long sessionId) {}

    record VietQrCheckOutResponse(long transactionId, long sessionId, String plateNumber, long fee,
                                  String exitedAt, long balanceAfter) {}

    record CashCheckOutResponse(long collectionId, long sessionId, String plateNumber, String studentCode,
                                String fullName, long amount, boolean overnight, String collectedAt, Long shiftId) {}

    private final PayParkingFeeByVietQrUseCase payByVietQr;
    private final CheckOutByCashUseCase checkOutByCash;
    private final ZoneId zone;

    GatePaymentController(PayParkingFeeByVietQrUseCase payByVietQr,
                          CheckOutByCashUseCase checkOutByCash,
                          Clock clock) {
        this.payByVietQr = payByVietQr;
        this.checkOutByCash = checkOutByCash;
        this.zone = clock.getZone();
    }

    @PostMapping("/vietqr")
    ResponseEntity<VietQrCheckOutResponse> checkOutByVietQr(
            @AuthenticationPrincipal SessionUser guard,
            @RequestHeader(value = IdempotencyKeyHeader.NAME, required = false) String key,
            @Valid @RequestBody SessionRequest body) {
        PayParkingFeeByVietQrUseCase.Result result = payByVietQr.payByVietQr(
                new PayParkingFeeByVietQrUseCase.Command(guard.id(), IdempotencyKeyHeader.parse(key), body.sessionId()));
        ParkingPayment p = result.payment();
        String plate = result.vehicleOwner() != null ? result.vehicleOwner().plate().value() : "";
        VietQrCheckOutResponse dto = new VietQrCheckOutResponse(
                p.id(), p.sessionId(), plate, p.points(), format(p.createdAt()), p.sourceBalanceAfter());
        return IdempotencyKeyHeader.created(dto, result.replayed());
    }

    @PostMapping("/cash")
    ResponseEntity<CashCheckOutResponse> checkOutByCash(
            @AuthenticationPrincipal SessionUser guard,
            @RequestHeader(value = IdempotencyKeyHeader.NAME, required = false) String key,
            @Valid @RequestBody SessionRequest body) {
        CheckOutByCashUseCase.Result result = checkOutByCash.checkOutByCash(
                new CheckOutByCashUseCase.Command(guard.id(), IdempotencyKeyHeader.parse(key), body.sessionId()));
        GateCashCollection c = result.collection();
        CashCheckOutResponse dto = new CashCheckOutResponse(
                c.id(),
                c.sessionId(),
                result.vehicle().plate().value(),
                result.vehicle().studentCode(),
                result.vehicle().fullName(),
                c.amount(),
                c.overnight(),
                format(c.collectedAt()),
                c.shiftId());
        return IdempotencyKeyHeader.created(dto, result.replayed());
    }

    private String format(Instant instant) {
        return OffsetDateTime.ofInstant(instant, zone).truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
