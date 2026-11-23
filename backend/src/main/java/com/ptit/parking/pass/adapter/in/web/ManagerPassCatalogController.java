package com.ptit.parking.pass.adapter.in.web;

import com.ptit.parking.pass.application.usecase.ManagePassCatalogUseCase;
import com.ptit.parking.pass.application.usecase.ManagePassCatalogUseCase.CatalogView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Quản lý giá vé tháng theo loại xe (quyền CASH_DEPOSIT). */
@RestController
@RequestMapping("/api/v1/manager")
class ManagerPassCatalogController {

    record PriceView(String vehicleTypeCode, String vehicleTypeName, long monthlyPoints) {}

    record CatalogResponse(List<PriceView> prices) {
        static CatalogResponse from(CatalogView view) {
            return new CatalogResponse(
                    view.prices().stream()
                            .map(p -> new PriceView(p.vehicleTypeCode(), p.vehicleTypeName(), p.monthlyPoints()))
                            .toList());
        }
    }

    record UpdatePriceRequest(
            @NotNull(message = "Vui lòng nhập giá vé tháng")
            @Min(value = 1000, message = "Giá vé tối thiểu 1.000 điểm")
            @Max(value = 10_000_000, message = "Giá vé tối đa 10.000.000 điểm")
            Long monthlyPoints) {}

    private final ManagePassCatalogUseCase catalog;

    ManagerPassCatalogController(ManagePassCatalogUseCase catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/pass-catalog")
    CatalogResponse view() {
        return CatalogResponse.from(catalog.view());
    }

    @PutMapping("/pass-prices/{vehicleTypeCode}")
    CatalogResponse updatePrice(@PathVariable String vehicleTypeCode, @Valid @RequestBody UpdatePriceRequest body) {
        return CatalogResponse.from(catalog.updatePrice(vehicleTypeCode, body.monthlyPoints()));
    }
}
