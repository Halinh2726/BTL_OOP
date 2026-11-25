package com.ptit.parking.parking.domain;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Decorator cho {@link FeePolicy} áp dụng quy tắc Mục 3.9:
 * <ul>
 *   <li>Khóa lấy xe từ mốc giờ cấu hình (mặc định 22:00 giờ Việt Nam).</li>
 *   <li>Lượt còn mở qua ngày hôm sau tính mức phí qua đêm cố định (mặc định 15.000 điểm tổng cộng),
 *       không cộng dồn với phí thường theo ngày.</li>
 * </ul>
 * <p>Nếu xe có vé tháng hợp lệ (phí 0 từ {@code inner}), giữ nguyên miễn phí? Chú ý: theo đặc tả 3.9
 * lượt qua đêm áp dụng mức 15.000 điểm tổng cộng thay cho phí thường; với xe có vé tháng, kiểm tra inner
 * trước nếu miễn phí thì không thu thêm, ngược lại nếu qua đêm thì trả {@link FeeQuote#overnightCharge(long)}.
 */
public final class OvernightAwareFeePolicy implements FeePolicy {

    private final FeePolicy inner;
    private final Supplier<ParkingPolicyConfig> configSupplier;
    private final ZoneId zone;

    public OvernightAwareFeePolicy(FeePolicy inner, Supplier<ParkingPolicyConfig> configSupplier, ZoneId zone) {
        this.inner = Objects.requireNonNull(inner, "inner");
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    @Override
    public long feeAt(ZonedDateTime exitTime) {
        return inner.feeAt(exitTime);
    }

    @Override
    public long minimumFee() {
        return inner.minimumFee();
    }

    @Override
    public FeeQuote quote(long vehicleId, ZonedDateTime exitTime) {
        return inner.quote(vehicleId, exitTime);
    }

    @Override
    public FeeQuote quoteSession(long vehicleId, Instant enteredAt, ZonedDateTime exitTime) {
        FeeQuote regular = inner.quote(vehicleId, exitTime);
        if (regular.isFree()) {
            return regular;
        }
        ParkingPolicyConfig cfg = configSupplier.get();
        if (enteredAt != null && cfg.isOvernight(enteredAt, exitTime, zone)) {
            return FeeQuote.overnightCharge(cfg.overnightFeePoints());
        }
        return regular;
    }

    @Override
    public boolean isCheckoutLocked(ZonedDateTime exitTime) {
        return configSupplier.get().isCurfewActive(exitTime, zone);
    }

    @Override
    public ParkingPolicyConfig policyConfig() {
        return configSupplier.get();
    }

    @Override
    public long minimumFeeFor(long vehicleId, ZonedDateTime at) {
        return inner.minimumFeeFor(vehicleId, at);
    }
}
