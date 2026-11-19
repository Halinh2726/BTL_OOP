package com.ptit.parking.parking.application;

import com.ptit.parking.parking.application.port.out.PassCoverage;
import com.ptit.parking.parking.domain.FeePolicy;
import com.ptit.parking.parking.domain.FeeQuote;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Đợt 5 — vé tháng theo mẫu <b>Decorator</b>: BỌC một {@link FeePolicy} có sẵn. Xe có vé tháng còn hiệu lực
 * (theo ngày giờ Việt Nam) thì miễn phí; không có thì hỏi tiếp chính sách bên trong. Bên trong là gì cũng được
 * (bảng giá theo giờ, hay chính sách khác sau này) — lớp này không phụ thuộc vào cách tính cụ thể.
 * <p>Xe vào hay xe ra đều chỉ thấy interface {@link FeePolicy}: thêm vé tháng KHÔNG sửa logic xe vào / xe ra, chỉ
 * đổi chỗ nối dây trong ParkingUseCaseConfig.
 */
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
    public long feeAt(ZonedDateTime exitTime) {
        return inner.feeAt(exitTime);
    }

    @Override
    public long minimumFee() {
        return inner.minimumFee();
    }

    @Override
    public FeeQuote quote(long vehicleId, ZonedDateTime exitTime) {
        return coverage(vehicleId, exitTime).map(FeeQuote::coveredByPass)
                .orElseGet(() -> inner.quote(vehicleId, exitTime));
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
