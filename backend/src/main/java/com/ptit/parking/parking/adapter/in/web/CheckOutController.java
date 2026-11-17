package com.ptit.parking.parking.adapter.in.web;

import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.VehicleOwner;
import com.ptit.parking.parking.application.usecase.CheckOutUseCase;
import com.ptit.parking.parking.application.usecase.PayParkingFeeByVietQrUseCase;
import com.ptit.parking.parking.application.usecase.PreviewCheckOutUseCase;
import com.ptit.parking.parking.application.usecase.PreviewCheckOutUseCase.Preview;
import com.ptit.parking.parking.application.usecase.QuickCheckOutUseCase;
import com.ptit.parking.parking.domain.ParkingSession;
import com.ptit.parking.parking.domain.VietQrOrder;
import com.ptit.parking.payment.domain.ParkingPayment;
import com.ptit.parking.shared.security.SessionUser;
import com.ptit.parking.shared.web.IdempotencyKeyHeader;
import com.ptit.parking.student.domain.PlateNumber;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

    /** Đơn thanh toán VietQR khi sinh viên không đủ điểm ví. */
    record VietQrOrderResponse(long sessionId, String plateNumber, String studentCode, String fullName,
                               long fee, String bankId, String accountNo, String accountName,
                               String transferContent, String qrCodeUrl) {

        static VietQrOrderResponse from(VietQrOrder order) {
            if (order == null) return null;
            return new VietQrOrderResponse(order.sessionId(), order.plateNumber(), order.studentCode(),
                    order.fullName(), order.fee(), order.bankId(), order.accountNo(),
                    order.accountName(), order.transferContent(), order.qrCodeUrl());
        }
    }

    /** Response của API #14. Trường nào không có (chưa đăng ký, không có lượt mở) thì null. */
    record PreviewResponse(Long sessionId, String plateNumber, String vehicleTypeCode, String studentCode,
                           String fullName, String enteredAt, Long balance, Long fee, boolean allowed,
                           String reason, VietQrOrderResponse vietQrOrder) {

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
                    preview.reason() == null ? null : preview.reason().name(),
                    VietQrOrderResponse.from(preview.vietQrOrder()));
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

    /** Body của API quick check-out: quét mã QR hoặc nhập biển số để thanh toán 1 chạm. */
    record QuickCheckOutRequest(
            @NotBlank(message = "Vui lòng nhập biển số hoặc quét mã")
            @Pattern(regexp = PlateNumber.PATTERN, message = "Biển số 5–20 ký tự, chỉ gồm chữ và số")
            String plateNumber) {

        QuickCheckOutRequest {
            plateNumber = PlateNumber.normalize(plateNumber);
        }
    }

    /** Response 201 của API quick check-out: đầy đủ thông tin biên lai 1 chạm cho màn hình bảo vệ / trạm xe bus. */
    record QuickCheckOutResponse(long transactionId, long sessionId, String plateNumber, String studentCode,
                                 String fullName, String vehicleTypeCode, long fee, String exitedAt,
                                 long balanceAfter) {

        static QuickCheckOutResponse from(ParkingPayment payment, VehicleOwner owner, ZoneId zone) {
            return new QuickCheckOutResponse(
                    payment.id(),
                    payment.sessionId(),
                    owner == null ? "" : owner.plate().value(),
                    owner == null ? "" : owner.studentCode(),
                    owner == null ? "" : owner.fullName(),
                    owner == null ? "" : owner.vehicleTypeCode(),
                    payment.points(),
                    format(payment.createdAt(), zone),
                    payment.sourceBalanceAfter());
        }

        static QuickCheckOutResponse from(QuickCheckOutUseCase.Result result, ZoneId zone) {
            return from(result.payment(), result.vehicleOwner(), zone);
        }
    }

    private final PreviewCheckOutUseCase previewCheckOut;
    private final CheckOutUseCase checkOut;
    private final QuickCheckOutUseCase quickCheckOut;
    private final PayParkingFeeByVietQrUseCase payParkingFeeByVietQr;
    private final ZoneId zone;

    CheckOutController(PreviewCheckOutUseCase previewCheckOut, CheckOutUseCase checkOut,
                       QuickCheckOutUseCase quickCheckOut, PayParkingFeeByVietQrUseCase payParkingFeeByVietQr,
                       Clock clock) {
        this.previewCheckOut = previewCheckOut;
        this.checkOut = checkOut;
        this.quickCheckOut = quickCheckOut;
        this.payParkingFeeByVietQr = payParkingFeeByVietQr;
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

    /** BV02-Quick — Xác nhận cho xe ra và thanh toán 1 chạm (One-Scan Flow) bằng biển số / mã QR. */
    @PostMapping("/quick")
    ResponseEntity<QuickCheckOutResponse> quickCheckOut(@AuthenticationPrincipal SessionUser guard,
                                                        @RequestHeader(value = IdempotencyKeyHeader.NAME, required = false) String key,
                                                        @Valid @RequestBody QuickCheckOutRequest body) {
        QuickCheckOutUseCase.Result result = quickCheckOut.checkOut(new QuickCheckOutUseCase.Command(
                guard.id(), IdempotencyKeyHeader.parse(key), body.plateNumber()));
        return IdempotencyKeyHeader.created(QuickCheckOutResponse.from(result, zone), result.replayed());
    }

    /** BV02-VietQR — Xác nhận thanh toán qua chuyển khoản VietQR khi sinh viên không đủ điểm ví. */
    @PostMapping("/vietqr")
    ResponseEntity<QuickCheckOutResponse> payVietQr(@AuthenticationPrincipal SessionUser guard,
                                                    @RequestHeader(value = IdempotencyKeyHeader.NAME, required = false) String key,
                                                    @Valid @RequestBody CheckOutRequest body) {
        PayParkingFeeByVietQrUseCase.Result result = payParkingFeeByVietQr.payByVietQr(new PayParkingFeeByVietQrUseCase.Command(
                guard.id(), IdempotencyKeyHeader.parse(key), body.sessionId()));
        return IdempotencyKeyHeader.created(QuickCheckOutResponse.from(result.payment(), result.vehicleOwner(), zone), result.replayed());
    }

    /** Giờ Việt Nam kèm múi giờ, ví dụ "2026-10-05T18:30:00+07:00" (CSDL lưu UTC). */
    private static String format(Instant instant, ZoneId zone) {
        return OffsetDateTime.ofInstant(instant, zone).truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
