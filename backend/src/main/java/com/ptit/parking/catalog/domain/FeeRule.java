package com.ptit.parking.catalog.domain;

import java.time.LocalTime;
import java.util.Objects;

/**
 * Một khung giá của bảng giá (R03): xe RA từ {@code startTime} (giờ Việt Nam) trở đi thì phí là {@code feePoints} điểm,
 * cho tới khung kế tiếp. Ví dụ: 00:00 → 3.000 điểm, 18:00 → 5.000 điểm.
 * <p>Cài đặt {@link Comparable}: thứ tự TỰ NHIÊN của các khung giá là theo giờ bắt đầu, nên nơi nào cần
 * sắp xếp chỉ việc gọi {@code sorted()} — quy tắc so sánh nằm cạnh dữ liệu, không lặp lại ở nhiều nơi.
 */
public record FeeRule(LocalTime startTime, int feePoints) implements Comparable<FeeRule> {

    public FeeRule {
        Objects.requireNonNull(startTime, "startTime");
        if (feePoints <= 0) {
            throw new IllegalArgumentException("feePoints phải > 0");
        }
    }

    @Override
    public int compareTo(FeeRule other) {
        return startTime.compareTo(other.startTime);
    }
}
