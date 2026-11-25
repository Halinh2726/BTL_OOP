package com.ptit.parking.parking.application;

import com.ptit.parking.parking.application.port.out.PassCoverage;
import com.ptit.parking.parking.domain.FeePolicy;
import com.ptit.parking.parking.domain.FeeQuote;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

/** Vé tháng miễn phí gửi xe theo ngày Việt Nam và dùng bảng giá cho các xe trả theo lượt. */
public final class PassAwareFeePolicy implements FeePolicy {

    private final FeePolicy inner;
    private final PassCoverage passes;
    private final ZoneId zone;

    public PassAwareFeePolicy(FeePolicy inner, PassCoverage passes, ZoneId zone) {
        this.inner = Objects.requireNonNull(inner, "inner");
        this.passes = Objects.requireNonNull(passes, "passes");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    @Override
    public long feeFor(Duration parkedFor) {
        return inner.feeFor(parkedFor);
    }

    @Override
    public long minimumFee() {
        return inner.minimumFee();
    }

    @Override
    public FeeQuote quote(long vehicleId, Instant enteredAt, ZonedDateTime exitTime) {
        return coverage(vehicleId, exitTime).map(FeeQuote::coveredByPass)
                .orElseGet(() -> inner.quote(vehicleId, enteredAt, exitTime));
    }

    /** Có vé tháng thì không cần điểm để vào (R04 chỉ áp dụng cho xe trả theo lượt). */
    @Override
    public long minimumFeeFor(long vehicleId, ZonedDateTime at) {
        return coverage(vehicleId, at).isPresent() ? 0 : inner.minimumFeeFor(vehicleId, at);
    }

    private Optional<LocalDate> coverage(long vehicleId, ZonedDateTime at) {
        return passes.coveredUntil(vehicleId, at.withZoneSameInstant(zone).toLocalDate());
    }
}
