package com.ptit.parking.shared.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Bộ đếm tần suất kiểu "cửa sổ trượt": mỗi khóa (IP, phiên...) được tối đa {@code limit} lần trong MỌI
 * khoảng {@code window} liên tiếp — không có kẽ hở "dồn 2 lần giới hạn quanh mốc đầu phút" như đếm theo phút cố định.
 * <p>Java thuần, không biết gì về HTTP hay Spring: dùng chung cho đăng nhập, đăng ký, giới hạn theo phiên,
 * và test được bằng đồng hồ giả. Lưu trong bộ nhớ của một máy chủ — đủ cho hệ thống chạy một instance.
 */
public class RateLimiter {

    private final int limit;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> hits = new HashMap<>();
    private Instant lastSweep;

    public RateLimiter(int limit, Duration window, Clock clock) {
        if (limit <= 0 || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("limit và window phải dương");
        }
        this.limit = limit;
        this.window = window;
        this.clock = clock;
        this.lastSweep = clock.instant();
    }

    /**
     * Chưa vượt giới hạn: ghi nhận một lần, trả rỗng. Đã đủ {@code limit} lần trong cửa sổ: KHÔNG ghi,
     * trả thời gian phải chờ tới khi lần cũ nhất hết hạn.
     * <p>synchronized: kiểm tra và ghi là MỘT bước — 10 request song song cũng không lọt quá giới hạn.
     */
    public synchronized Optional<Duration> tryAcquire(String key) {
        Instant now = clock.instant();
        sweepIfDue(now);
        Deque<Instant> times = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        dropExpired(times, now);
        if (times.size() >= limit) {
            return Optional.of(Duration.between(now, times.peekFirst().plus(window)));
        }
        times.addLast(now);
        return Optional.empty();
    }

    /** Xóa bộ đếm của một khóa (ví dụ: đăng nhập đúng thì các lần sai trước đó không tính nữa). */
    public synchronized void reset(String key) {
        hits.remove(key);
    }

    /** Số khóa đang được theo dõi — chỉ để test kiểm tra bộ nhớ không phình mãi. */
    synchronized int trackedKeys() {
        return hits.size();
    }

    private void dropExpired(Deque<Instant> times, Instant now) {
        Instant cutoff = now.minus(window);
        while (!times.isEmpty() && !times.peekFirst().isAfter(cutoff)) {
            times.pollFirst();
        }
    }

    /** Mỗi cửa sổ dọn một lần các khóa không còn lần nào: IP / phiên cũ không nằm trong bộ nhớ mãi. */
    private void sweepIfDue(Instant now) {
        if (now.isBefore(lastSweep.plus(window))) {
            return;
        }
        hits.values().forEach(times -> dropExpired(times, now));
        hits.values().removeIf(Deque::isEmpty);
        lastSweep = now;
    }
}
