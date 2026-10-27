package com.ptit.parking.parking.adapter.in.web;

import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.VehicleOwner;
import com.ptit.parking.parking.application.usecase.PreviewCheckInUseCase;
import com.ptit.parking.parking.application.usecase.PreviewCheckInUseCase.Preview;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    private final PreviewCheckInUseCase previewCheckIn;

    CheckInController(PreviewCheckInUseCase previewCheckIn) {
        this.previewCheckIn = previewCheckIn;
    }

    /** API #12 — tra cứu trước khi cho xe vào. Chỉ đọc. */
    @GetMapping("/preview")
    PreviewResponse preview(@RequestParam String plate) {
        return PreviewResponse.from(previewCheckIn.preview(plate));
    }
}