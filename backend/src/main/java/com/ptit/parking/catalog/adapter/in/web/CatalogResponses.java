package com.ptit.parking.catalog.adapter.in.web;

import com.ptit.parking.catalog.domain.FeeRule;
import com.ptit.parking.catalog.domain.VehicleType;
import com.ptit.parking.payment.domain.ExchangeRate;

/**
 * Dữ liệu JSON của các API danh mục #9, #10, #11.
 */
final class CatalogResponses {

    private CatalogResponses() {
    }

    record VehicleTypeResponse(String code, String name) {

        static VehicleTypeResponse from(VehicleType type) {
            return new VehicleTypeResponse(type.code(), type.name());
        }
    }

    record FeeRuleResponse(int upToMinutes, int feePoints) {

        static FeeRuleResponse from(FeeRule rule) {
            return new FeeRuleResponse(rule.upToMinutes(), rule.feePoints());
        }
    }

    /** Chỉ một trường cashPerPoint — không trả ví nhận phí (R06). */
    record ExchangeRateResponse(int cashPerPoint) {

        static ExchangeRateResponse from(ExchangeRate rate) {
            return new ExchangeRateResponse(rate.cashPerPoint());
        }
    }
}