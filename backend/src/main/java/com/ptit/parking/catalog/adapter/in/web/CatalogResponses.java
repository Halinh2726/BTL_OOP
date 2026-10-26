package com.ptit.parking.catalog.adapter.in.web;

import java.time.format.DateTimeFormatter;

import com.ptit.parking.catalog.domain.FeeRule;
import com.ptit.parking.catalog.domain.VehicleType;
import com.ptit.parking.payment.domain.ExchangeRate;

/**
 * Response của API danh mục #9, #10, #11 — đúng các trường của Đặc tả API.
 * Tách khỏi lớp domain: sửa domain không làm vỡ "hợp đồng" JSON mà giao diện đang dùng.
 */
final class CatalogResponses {

    private CatalogResponses() {
    }

    record VehicleTypeResponse(String code, String name) {

        static VehicleTypeResponse from(VehicleType type) {
            return new VehicleTypeResponse(type.code(), type.name());
        }
    }

    record FeeRuleResponse(String startTime, int feePoints) {

        private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

        static FeeRuleResponse from(FeeRule rule) {
            return new FeeRuleResponse(rule.startTime().format(HH_MM), rule.feePoints());
        }
    }

    /** Chỉ một trường cashPerPoint — không trả ví nhận phí (R06). */
    record ExchangeRateResponse(int cashPerPoint) {

        static ExchangeRateResponse from(ExchangeRate rate) {
            return new ExchangeRateResponse(rate.cashPerPoint());
        }
    }
}