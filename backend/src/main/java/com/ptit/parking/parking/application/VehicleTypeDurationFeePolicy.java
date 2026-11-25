package com.ptit.parking.parking.application;

import com.ptit.parking.catalog.application.port.out.FeeRuleRepository;
import com.ptit.parking.catalog.domain.FeeRule;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.VehicleOwner;
import com.ptit.parking.parking.domain.DurationFeePolicy;
import com.ptit.parking.parking.domain.FeePolicy;
import com.ptit.parking.parking.domain.FeeQuote;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Chính sách tính phí gửi lượt đọc động theo loại xe từ {@link FeeRuleRepository}, nên Quản lý vừa cập nhật
 * bảng giá là có hiệu lực ngay lập tức mà không cần khởi động lại máy chủ.
 */
public final class VehicleTypeDurationFeePolicy implements FeePolicy {

    private static final String DEFAULT_TYPE = "XE_MAY";

    private final FeeRuleRepository feeRules;
    private final VehicleOwnerDirectory vehicles;

    public VehicleTypeDurationFeePolicy(FeeRuleRepository feeRules, VehicleOwnerDirectory vehicles) {
        this.feeRules = Objects.requireNonNull(feeRules, "feeRules");
        this.vehicles = Objects.requireNonNull(vehicles, "vehicles");
    }

    @Override
    public long feeFor(Duration parkedFor) {
        return defaultPolicy().feeFor(parkedFor);
    }

    @Override
    public long minimumFee() {
        return defaultPolicy().minimumFee();
    }

    @Override
    public FeeQuote quote(long vehicleId, Instant enteredAt, ZonedDateTime exitTime) {
        return FeeQuote.charge(policyForVehicle(vehicleId).feeFor(Duration.between(enteredAt, exitTime.toInstant())));
    }

    @Override
    public long minimumFeeFor(long vehicleId, ZonedDateTime at) {
        return policyForVehicle(vehicleId).minimumFee();
    }

    private DurationFeePolicy policyForVehicle(long vehicleId) {
        String vehicleTypeCode = vehicles.findByVehicleId(vehicleId)
                .map(VehicleOwner::vehicleTypeCode)
                .orElse(DEFAULT_TYPE);
        List<FeeRule> rules = feeRules.rulesForVehicleType(vehicleTypeCode);
        if (rules.isEmpty()) {
            rules = feeRules.allFeeRules();
        }
        return new DurationFeePolicy(rules);
    }

    private DurationFeePolicy defaultPolicy() {
        return new DurationFeePolicy(feeRules.allFeeRules());
    }
}
