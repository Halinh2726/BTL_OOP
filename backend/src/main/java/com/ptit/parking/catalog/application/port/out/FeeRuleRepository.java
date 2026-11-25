package com.ptit.parking.catalog.application.port.out;

import com.ptit.parking.catalog.domain.FeeRule;
import java.util.List;
import java.util.Map;

/** Port: đọc và cập nhật bảng giá gửi lượt theo từng loại xe. */
@FunctionalInterface
public interface FeeRuleRepository {

    List<FeeRule> allFeeRules();

    default List<FeeRule> rulesForVehicleType(String vehicleTypeCode) {
        return allFeeRules();
    }

    default Map<String, List<FeeRule>> allByVehicleType() {
        return Map.of("XE_MAY", allFeeRules());
    }

    default void replaceRulesForVehicleType(String vehicleTypeCode, List<FeeRule> rules) {
        throw new UnsupportedOperationException("replaceRulesForVehicleType chưa được cài đặt");
    }
}