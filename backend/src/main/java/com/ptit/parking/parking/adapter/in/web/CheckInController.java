package com.ptit.parking.parking.adapter.in.web;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.VehicleOwner;
import com.ptit.parking.parking.application.usecase.CheckInUseCase;
import com.ptit.parking.parking.application.usecase.PreviewCheckInUseCase;
import com.ptit.parking.parking.application.usecase.PreviewCheckInUseCase.Preview;
import com.ptit.parking.shared.security.SessionUser;
import com.ptit.parking.student.domain.PlateNumber;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** BV01 — bảo vệ xác minh xe vào. Quyền PARKING_CHECK_IN (ma trận ProtectedEndpoint). */
@RestController
@RequestMapping("/api/v1/guard/check-in")
class CheckInController {

    /** Response của API #12. Thông tin chủ xe là null khi biển số chưa đăng ký. */
    record PreviewResponse(String plateNumber, String vehicleTypeCode, String studentCode, String fullName,
                           Long balance, long minFee, boolean allowed, String reason) {

        static PreviewResponse from(Preview preview) {
            VehicleOwner owner = preview.owner();
            return new PreviewResponse(
                    owner == null ? null : owner.plate().value(),
                    owner == null ? null : owner.vehicleTypeCode(),
                    owner == null ? null : owner.studentCode(),
                    owner == null ? null : owner.fullName(),
                    preview.balance(), preview.minFee(), preview.allowed(),
                    preview.reason() == null ? null : preview.reason().name());
        }
    }

    /** Body của API #13. Biển số chuẩn hóa TRƯỚC khi kiểm tra định dạng, cùng quy tắc với {@link PlateNumber}. */
    record CheckInRequest(
            @NotBlank(message = "Vui lòng nhập biển số")
            @Pattern(regexp = PlateNumber.PATTERN, message = "Biển số 5–20 ký tự, chỉ gồm chữ và số")
            String plateNumber) {

        CheckInRequest {
            plateNumber = PlateNumber.normalize(plateNumber);
        }
    }

    /** Response 201 của API #13. enteredAt theo giờ Việt Nam kèm múi giờ (CSDL lưu UTC). */
    record CheckInResponse(long sessionId, String plateNumber, String enteredAt) {

        static CheckInResponse from(CheckInUseCase.Result result, ZoneId zone) {
            return new CheckInResponse(result.session().id(), result.vehicle().plate().value(),
                    OffsetDateTime.ofInstant(result.session().enteredAt(), zone).truncatedTo(ChronoUnit.SECONDS)
                            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
    }

    private final PreviewCheckInUseCase previewCheckIn;
    private final CheckInUseCase checkIn;
    private final ZoneId zone;

    CheckInController(PreviewCheckInUseCase previewCheckIn, CheckInUseCase checkIn, Clock clock) {
        this.previewCheckIn = previewCheckIn;
        this.checkIn = checkIn;
        this.zone = clock.getZone();
    }

    /** API #12 — tra cứu trước khi cho xe vào. Chỉ đọc. */
    @GetMapping("/preview")
    PreviewResponse preview(@RequestParam String plate) {
        return PreviewResponse.from(previewCheckIn.preview(plate));
    }

    /** API #13 — xác nhận cho xe vào. Bảo vệ cho vào là người đang đăng nhập, lấy từ PHIÊN. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CheckInResponse checkIn(@AuthenticationPrincipal SessionUser guard, @Valid @RequestBody CheckInRequest body) {
        return CheckInResponse.from(checkIn.checkIn(new CheckInUseCase.Command(guard.id(), body.plateNumber())), zone);
    }
}
