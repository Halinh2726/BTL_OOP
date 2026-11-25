package com.ptit.parking.catalog.presentation.web;

import com.ptit.parking.catalog.application.usecase.GetCatalogUseCase.VehicleTypeFeeSchedule;
import com.ptit.parking.catalog.application.usecase.ManageFeeCatalogUseCase;
import com.ptit.parking.catalog.domain.FeeRule;
import com.ptit.parking.catalog.presentation.web.CatalogResponses.VehicleTypeFeeScheduleResponse;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Quản lý cấu hình bảng giá gửi xe theo lượt theo từng loại xe (quyền CASH_DEPOSIT). */
@RestController
@RequestMapping("/api/v1/manager")
class ManagerFeeCatalogController {

    record TierInput(
            @NotNull(message = "Vui lòng nhập mốc thời lượng (phút)")
            @Min(value = 1, message = "Thời lượng tối thiểu 1 phút")
            @Max(value = 1440, message = "Thời lượng tối đa 1440 phút (24 giờ)")
            Integer upToMinutes,

            @NotNull(message = "Vui lòng nhập mức phí")
            @Min(value = 100, message = "Mức phí tối thiểu 100 điểm")
            @Max(value = 10_000_000, message = "Mức phí tối đa 10.000.000 điểm")
            Integer feePoints) {}

    record UpdateFeeScheduleRequest(
            @NotEmpty(message = "Bảng giá phải có ít nhất 1 bậc")
            List<@Valid TierInput> rules) {}

    record FeeCatalogResponse(List<VehicleTypeFeeScheduleResponse> schedules) {
        static FeeCatalogResponse from(List<VehicleTypeFeeSchedule> schedules) {
            return new FeeCatalogResponse(schedules.stream().map(VehicleTypeFeeScheduleResponse::from).toList());
        }
    }

    private final ManageFeeCatalogUseCase manageFeeCatalog;

    ManagerFeeCatalogController(ManageFeeCatalogUseCase manageFeeCatalog) {
        this.manageFeeCatalog = manageFeeCatalog;
    }

    @GetMapping("/fee-rules")
    FeeCatalogResponse view() {
        return FeeCatalogResponse.from(manageFeeCatalog.view());
    }

    @PutMapping("/fee-rules/{vehicleTypeCode}")
    FeeCatalogResponse update(@PathVariable String vehicleTypeCode,
                              @Valid @RequestBody UpdateFeeScheduleRequest body) {
        List<FeeRule> parsed;
        try {
            parsed = body.rules().stream()
                    .map(tier -> new FeeRule(tier.upToMinutes(), tier.feePoints()))
                    .toList();
        } catch (IllegalArgumentException ex) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, ex.getMessage());
        }
        return FeeCatalogResponse.from(manageFeeCatalog.update(vehicleTypeCode, parsed));
    }
}
