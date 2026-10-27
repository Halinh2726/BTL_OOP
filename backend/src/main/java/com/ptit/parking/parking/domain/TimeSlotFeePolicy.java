package com.ptit.parking.parking.domain;

import com.ptit.parking.catalog.domain.FeeRule;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

/**
 * GiaTheoKhungGio — cài đặt hiện tại của {@link FeePolicy} theo R03: phí phụ thuộc DUY NHẤT vào giờ xe ra
 * (giờ Việt Nam), không phụ thuộc thời gian gửi hay loại xe. Với bảng giá mặc định:
 * ra trước 18:00 → 3.000 điểm; từ 18:00 trở đi → 5.000 điểm.
 * <p>Bảng giá là DỮ LIỆU (bảng fee_rules), không hóa cứng con số trong code: thêm khung 22:00 → 7.000
 * chỉ cần thêm một dòng CSDL, lớp này không đổi.
 */
public final class TimeSlotFeePolicy implements FeePolicy {

    private final List<FeeRule> rules;     // đã sắp theo giờ bắt đầu tăng dần
    private final ZoneId zone;

    public TimeSlotFeePolicy(List<FeeRule> rules, ZoneId zone) {
        this.rules = rules.stream().sorted().toList();
        this.zone = Objects.requireNonNull(zone, "zone");
        // Mọi thời điểm trong ngày phải thuộc một khung -> bắt buộc có khung bắt đầu lúc 00:00
        if (this.rules.isEmpty() || !this.rules.get(0).startTime().equals(LocalTime.MIDNIGHT)) {
            throw new IllegalArgumentException("Bảng giá phải có khung bắt đầu lúc 00:00");
        }
    }

    /** Khung áp dụng là khung CUỐI CÙNG có giờ bắt đầu ≤ giờ ra (đổi sang giờ Việt Nam trước khi so). */
    @Override
    public long feeAt(ZonedDateTime exitTime) {
        LocalTime exitAt = exitTime.withZoneSameInstant(zone).toLocalTime();
        FeeRule applicable = rules.get(0);
        for (FeeRule rule : rules) {
            if (rule.startTime().isAfter(exitAt)) {
                break;
            }
            applicable = rule;
        }
        return applicable.feePoints();
    }

    @Override
    public long minimumFee() {
        return rules.stream().mapToLong(FeeRule::feePoints).min().orElseThrow();
    }
}