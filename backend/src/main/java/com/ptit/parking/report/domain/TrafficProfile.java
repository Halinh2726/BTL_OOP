package com.ptit.parking.report.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Value Object: lượt xe vào / ra theo từng giờ trong ngày (0–23), cộng dồn qua {@code days} ngày.
 * Tự trả lời các câu hỏi nghiệp vụ: trung bình mỗi giờ, giờ cao điểm, trung bình mỗi ngày.
 */
public record TrafficProfile(int days, List<Long> entries, List<Long> exits) {

    public static final int HOURS = 24;

    public TrafficProfile {
        if (days < 1) {
            throw new IllegalArgumentException("days phải >= 1");
        }
        if (entries.size() != HOURS || exits.size() != HOURS) {
            throw new IllegalArgumentException("Cần đủ 24 giờ");
        }
        entries = List.copyOf(entries);
        exits = List.copyOf(exits);
    }

    /** Từ kết quả GROUP BY giờ (giờ thiếu = 0 lượt). */
    public static TrafficProfile of(int days, Map<Integer, Long> entriesByHour, Map<Integer, Long> exitsByHour) {
        return new TrafficProfile(days, fill(entriesByHour), fill(exitsByHour));
    }

    private static List<Long> fill(Map<Integer, Long> byHour) {
        List<Long> hours = new ArrayList<>(HOURS);
        for (int hour = 0; hour < HOURS; hour++) {
            hours.add(byHour.getOrDefault(hour, 0L));
        }
        return hours;
    }

    public long totalEntries() {
        return entries.stream().mapToLong(Long::longValue).sum();
    }

    public long totalExits() {
        return exits.stream().mapToLong(Long::longValue).sum();
    }

    /** Trung bình số lượt xe vào mỗi ngày. */
    public double averageEntriesPerDay() {
        return (double) totalEntries() / days;
    }

    public double averageEntriesAt(int hour) {
        return (double) entries.get(hour) / days;
    }

    public double averageExitsAt(int hour) {
        return (double) exits.get(hour) / days;
    }

    /**
     * Tối đa {@code limit} giờ đông xe VÀO nhất (giờ không có xe bị bỏ qua); bằng nhau thì giờ sớm hơn đứng trước.
     * Dùng để xếp thêm người trực.
     */
    public List<Integer> peakHours(int limit) {
        return IntStream.range(0, HOURS).boxed()
                .filter(hour -> entries.get(hour) > 0)
                .sorted(Comparator.comparingLong((Integer hour) -> entries.get(hour)).reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .limit(limit)
                .toList();
    }
}
