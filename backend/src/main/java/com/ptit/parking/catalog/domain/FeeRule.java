package com.ptit.parking.catalog.domain;

/** Mức phí của một bậc thời lượng. Cận trên được tính vào bậc hiện tại. */
public record FeeRule(int upToMinutes, int feePoints) implements Comparable<FeeRule> {

    public static final int MINUTES_PER_DAY = 24 * 60;

    public FeeRule {
        if (upToMinutes < 1 || upToMinutes > MINUTES_PER_DAY) {
            throw new IllegalArgumentException("upToMinutes phải nằm trong khoảng 1 đến 1440");
        }
        if (feePoints <= 0) {
            throw new IllegalArgumentException("feePoints phải > 0");
        }
    }

    @Override
    public int compareTo(FeeRule other) {
        return Integer.compare(upToMinutes, other.upToMinutes);
    }
}
