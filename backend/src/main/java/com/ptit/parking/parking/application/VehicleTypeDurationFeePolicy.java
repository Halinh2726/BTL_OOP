package com.ptit.parking.parking.application;

import com.ptit.parking.catalog.application.port.out.FeeRuleRepository;
import com.ptit.parking.catalog.domain.FeeRule;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.VehicleOwner;
import com.ptit.parking.parking.domain.FeePolicy;
import com.ptit.parking.parking.domain.FeeQuote;
import com.ptit.parking.parking.domain.TimeSlotFeePolicy;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Chính sách tính phí gửi lượt theo khung giờ và cộng dồn qua đêm, đọc động theo từng loại xe từ
 * {@link FeeRuleRepository}, nên Quản lý vừa cập nhật bảng giá là có hiệu lực ngay lập tức.
 */
public final class VehicleTypeDurationFeePolicy implements FeePolicy {

    private static final String DEFAULT_TYPE = "XE_MAY";
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final FeeRuleRepository feeRules;
    private final VehicleOwnerDirectory vehicles;
    private final ZoneId zone;

    public VehicleTypeDurationFeePolicy(FeeRuleRepository feeRules, VehicleOwnerDirectory vehicles) {
        this(feeRules, vehicles, DEFAULT_ZONE);
    }

    public VehicleTypeDurationFeePolicy(FeeRuleRepository feeRules, VehicleOwnerDirectory vehicles, ZoneId zone) {
        this.feeRules = Objects.requireNonNull(feeRules, "feeRules");
        this.vehicles = Objects.requireNonNull(vehicles, "vehicles");
        this.zone = Objects.requireNonNull(zone, "zone");
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
        return policyForVehicle(vehicleId).quote(vehicleId, enteredAt, exitTime);
    }

    @Override
    public FeeQuote quoteSession(long vehicleId, Instant enteredAt, ZonedDateTime exitTime) {
        return policyForVehicle(vehicleId).quoteSession(vehicleId, enteredAt, exitTime);
    }

    @Override
    public long minimumFeeFor(long vehicleId, ZonedDateTime at) {
        return policyForVehicle(vehicleId).minimumFee();
    }

    private TimeSlotFeePolicy policyForVehicle(long vehicleId) {
        String vehicleTypeCode = vehicles.findByVehicleId(vehicleId)
                .map(VehicleOwner::vehicleTypeCode)
                .orElse(DEFAULT_TYPE);
        List<FeeRule> rules = feeRules.rulesForVehicleType(vehicleTypeCode);
        if (rules.isEmpty()) {
            rules = feeRules.allFeeRules();
        }
        return new TimeSlotFeePolicy(rules, zone);
    }

    private TimeSlotFeePolicy defaultPolicy() {
        return new TimeSlotFeePolicy(feeRules.allFeeRules(), zone);
    }
}
