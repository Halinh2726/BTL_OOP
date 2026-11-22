package com.ptit.parking.parking.domain;

import com.ptit.parking.catalog.domain.FeeRule;
import java.time.Duration;
import java.util.List;

/** Tính phí từng chu kỳ 24 giờ từ lúc xe vào bãi. Phần lẻ áp dụng lại bảng giá từ bậc đầu. */
public final class DurationFeePolicy implements FeePolicy {

    private final List<FeeRule> rules;

    public DurationFeePolicy(List<FeeRule> rules) {
        this.rules = rules.stream().sorted().toList();
        if (this.rules.isEmpty() || this.rules.getLast().upToMinutes() != FeeRule.MINUTES_PER_DAY) {
            throw new IllegalArgumentException("Bảng giá phải có bậc cuối đến 24 giờ");
        }
        for (int i = 1; i < this.rules.size(); i++) {
            FeeRule previous = this.rules.get(i - 1);
            FeeRule current = this.rules.get(i);
            if (current.upToMinutes() == previous.upToMinutes() || current.feePoints() < previous.feePoints()) {
                throw new IllegalArgumentException("Mốc thời lượng phải khác nhau và phí không được giảm");
            }
        }
    }

    @Override
    public long feeFor(Duration parkedFor) {
        if (parkedFor.isNegative()) {
            throw new IllegalArgumentException("Giờ ra trước giờ vào");
        }

        long fullDays = parkedFor.toDays();
        long dailyPoints = rules.getLast().feePoints();
        long fullDayPoints = Math.multiplyExact(fullDays, dailyPoints);
        Duration remainder = parkedFor.minusDays(fullDays);
        if (fullDays > 0 && remainder.isZero()) {
            return fullDayPoints;
        }

        FeeRule applicable = rules.stream()
                .filter(rule -> remainder.compareTo(Duration.ofMinutes(rule.upToMinutes())) <= 0)
                .findFirst()
                .orElseThrow();
        return Math.addExact(fullDayPoints, applicable.feePoints());
    }

    @Override
    public long minimumFee() {
        return rules.getFirst().feePoints();
    }
}
