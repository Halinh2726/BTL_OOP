package com.ptit.parking.catalog.adapter.in.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ptit.parking.catalog.adapter.in.web.CatalogResponses.ExchangeRateResponse;
import com.ptit.parking.catalog.adapter.in.web.CatalogResponses.FeeRuleResponse;
import com.ptit.parking.catalog.adapter.in.web.CatalogResponses.VehicleTypeResponse;
import com.ptit.parking.catalog.application.usecase.GetCatalogUseCase;

/**
 * API #9, #10, #11 — danh mục chỉ đọc. Quyền: chỉ cần đã đăng nhập (luật "/api/** authenticated"
 * trong SecurityConfig), nên các API này KHÔNG có trong ma trận ProtectedEndpoint.
 */
@RestController
@RequestMapping("/api/v1")
class CatalogController {

    private final GetCatalogUseCase catalog;

    CatalogController(GetCatalogUseCase catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/vehicle-types")
    List<VehicleTypeResponse> vehicleTypes() {
        return catalog.vehicleTypes().stream().map(VehicleTypeResponse::from).toList();
    }

    @GetMapping("/fee-rules")
    List<FeeRuleResponse> feeRules() {
        return catalog.feeRules().stream().map(FeeRuleResponse::from).toList();
    }

    @GetMapping("/config/exchange-rate")
    ExchangeRateResponse exchangeRate() {
        return ExchangeRateResponse.from(catalog.exchangeRate());
    }
}