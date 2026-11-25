package com.ptit.parking.catalog.domain;

import java.util.List;

/** Mức phí của một bậc thời lượng. Cận trên được tính vào bậc hiện tại. */
public record FeeRule(int upToMinutes, int feePoints) implements Comparable<FeeRule> {

    public static final int DAY_SLOT_MINUTES = 18 * 60;
    public static final int EVENING_SLOT_MINUTES = 22 * 60;
    public static final int MINUTES_PER_DAY = 24 * 60;
    public static final int OVERNIGHT_SLOT_MINUTES = MINUTES_PER_DAY;

    public FeeRule {
        if (upToMinutes < 1 || upToMinutes > MINUTES_PER_DAY) {
            throw new IllegalArgumentException("upToMinutes phải nằm trong khoảng 1 đến 1440");
        }
        if (feePoints <= 0) {
            throw new IllegalArgumentException("feePoints phải > 0");
        }
    }

    /** Chuẩn hóa (sắp xếp tăng dần) và kiểm tra bất biến của một bảng giá lượt trong chu kỳ 24 giờ. */
    public static List<FeeRule> validateSchedule(List<FeeRule> rules) {
        if (rules == null || rules.isEmpty()) {
            throw new IllegalArgumentException("Bảng giá phải có ít nhất 1 bậc");
        }
        List<FeeRule> sorted = rules.stream().sorted().toList();
        if (sorted.getLast().upToMinutes() != MINUTES_PER_DAY) {
            throw new IllegalArgumentException("Bảng giá phải có bậc cuối đến 24 giờ (1440 phút)");
        }
        for (int i = 1; i < sorted.size(); i++) {
            FeeRule previous = sorted.get(i - 1);
            FeeRule current = sorted.get(i);
            if (current.upToMinutes() == previous.upToMinutes() || current.feePoints() < previous.feePoints()) {
                throw new IllegalArgumentException("Mốc thời lượng phải khác nhau và phí không được giảm theo bậc");
            }
        }
        return sorted;
    }

    /** Kiểm tra bảng giá gồm đúng 3 khung giờ cố định: trước 18:00 (1080), 18:00–22:00 (1320) và qua đêm sau 22:00 (1440). */
    public static List<FeeRule> validateTimeSlotSchedule(List<FeeRule> rules) {
        List<FeeRule> sorted = validateSchedule(rules);
        if (sorted.size() != 3
                || sorted.get(0).upToMinutes() != DAY_SLOT_MINUTES
                || sorted.get(1).upToMinutes() != EVENING_SLOT_MINUTES
                || sorted.get(2).upToMinutes() != OVERNIGHT_SLOT_MINUTES) {
            throw new IllegalArgumentException(
                    "Bảng giá phải gồm đúng 3 khung cố định: trước 18:00 (1080), 18:00–22:00 (1320) và qua đêm sau 22:00 (1440)");
        }
        return sorted;
    }

    @Override
    public int compareTo(FeeRule other) {
        return Integer.compare(upToMinutes, other.upToMinutes);
    }
}
