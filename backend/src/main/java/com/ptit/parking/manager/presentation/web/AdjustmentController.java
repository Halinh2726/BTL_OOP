package com.ptit.parking.manager.presentation.web;

import com.ptit.parking.manager.application.usecase.AdjustPointsUseCase;
import com.ptit.parking.manager.application.usecase.AdjustPointsUseCase.Direction;
import com.ptit.parking.payment.domain.PointAdjustment;
import com.ptit.parking.shared.infrastructure.security.SessionUser;
import com.ptit.parking.shared.presentation.web.IdempotencyKeyHeader;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Clock;
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

/** Đợt 3 — giao dịch điều chỉnh (quyền POINT_ADJUST), có Idempotency-Key như mọi API thay đổi điểm. */
@RestController
@RequestMapping("/api/v1/manager/adjustments")
class AdjustmentController {

    record AdjustRequest(
            @NotBlank(message = "Vui lòng nhập MSSV")
            @Size(max = 20, message = "MSSV tối đa 20 ký tự")
            String studentCode,

            @NotNull(message = "Vui lòng chọn loại điều chỉnh")
            Direction direction,

            @NotNull(message = "Vui lòng nhập số điểm")
            @Positive(message = "Số điểm phải lớn hơn 0")
            @Max(value = 10_000_000, message = "Tối đa 10.000.000 điểm mỗi lần")
            Long points,

            @NotBlank(message = "Bắt buộc ghi lý do điều chỉnh")
            @Size(max = PointAdjustment.MAX_REASON, message = "Lý do tối đa 500 ký tự")
            String reason,

            @Positive(message = "Mã giao dịch gốc phải > 0")
            Long originalTransactionId) {
    }

    record AdjustResponse(long transactionId, String studentCode, String studentName, String direction, long points,
                          String reason, Long originalTransactionId, String createdAt) {}

    private final AdjustPointsUseCase adjustPoints;
    private final ZoneId zone;

    AdjustmentController(AdjustPointsUseCase adjustPoints, Clock clock) {
        this.adjustPoints = adjustPoints;
        this.zone = clock.getZone();
    }

    @PostMapping
    ResponseEntity<AdjustResponse> adjust(@AuthenticationPrincipal SessionUser manager,
                                          @RequestHeader(value = IdempotencyKeyHeader.NAME, required = false) String key,
                                          @Valid @RequestBody AdjustRequest body) {
        AdjustPointsUseCase.Result result = adjustPoints.adjust(new AdjustPointsUseCase.Command(manager.id(),
                IdempotencyKeyHeader.parse(key), body.studentCode(), body.direction(), body.points(), body.reason(),
                body.originalTransactionId()));
        PointAdjustment adjustment = result.adjustment();
        AdjustResponse response = new AdjustResponse(adjustment.id(), result.studentCode(), result.studentName(),
                body.direction().name(), adjustment.points(), adjustment.reason(), adjustment.originalTransactionId(),
                OffsetDateTime.ofInstant(adjustment.createdAt(), zone).truncatedTo(ChronoUnit.SECONDS)
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        return IdempotencyKeyHeader.created(response, result.replayed());
    }
}
