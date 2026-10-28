package com.ptit.parking.shared.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Cửa sổ trượt: đúng giới hạn, đúng thời gian chờ, khóa độc lập, bộ nhớ được dọn. Đồng hồ giả, không cần chờ thật. */
class RateLimiterTest {

    private final MovableClock clock = new MovableClock(Instant.parse("2026-10-27T03:00:00Z"));
    private final RateLimiter limiter = new RateLimiter(3, Duration.ofSeconds(60), clock);

    @Test
    void choPhepDungGioiHan_lanTiepTheoBiChan_kemThoiGianCho() {
        assertThat(limiter.tryAcquire("ip-1")).isEmpty();          // giây 0
        clock.advance(10);
        assertThat(limiter.tryAcquire("ip-1")).isEmpty();          // giây 10
        clock.advance(10);
        assertThat(limiter.tryAcquire("ip-1")).isEmpty();          // giây 20

        clock.advance(10);                                          // giây 30
        assertThat(limiter.tryAcquire("ip-1")).contains(Duration.ofSeconds(30));   // lần ở giây 0 hết hạn ở giây 60
    }

    @Test
    void cuaSoTruot_lanCuNhatHetHan_thiDuocThemDungMotLan() {
        limiter.tryAcquire("ip-1");                                 // giây 0
        clock.advance(10);
        limiter.tryAcquire("ip-1");                                 // giây 10
        limiter.tryAcquire("ip-1");                                 // giây 10

        clock.advance(50);                                          // giây 60: lần ở giây 0 vừa hết hạn
        assertThat(limiter.tryAcquire("ip-1")).isEmpty();
        assertThat(limiter.tryAcquire("ip-1")).contains(Duration.ofSeconds(10));   // còn 2 lần ở giây 10
    }

    @Test
    void lanBiChanKhongDuocGhi_khongKeoDaiThoiGianCho() {
        RateLimiter one = new RateLimiter(1, Duration.ofSeconds(60), clock);
        one.tryAcquire("ip-1");

        clock.advance(30);
        assertThat(one.tryAcquire("ip-1")).contains(Duration.ofSeconds(30));
        clock.advance(29);
        assertThat(one.tryAcquire("ip-1")).contains(Duration.ofSeconds(1));
        clock.advance(1);
        assertThat(one.tryAcquire("ip-1")).isEmpty();
    }

    @Test
    void moiKhoaDemRieng() {
        limiter.tryAcquire("ip-1");
        limiter.tryAcquire("ip-1");
        limiter.tryAcquire("ip-1");

        assertThat(limiter.tryAcquire("ip-1")).isPresent();
        assertThat(limiter.tryAcquire("ip-2")).isEmpty();
    }

    @Test
    void reset_xoaBoDemCuaMotKhoa() {
        limiter.tryAcquire("ip-1");
        limiter.tryAcquire("ip-1");
        limiter.tryAcquire("ip-1");

        limiter.reset("ip-1");

        assertThat(limiter.tryAcquire("ip-1")).isEmpty();
    }

    @Test
    void khoaCuDuocDon_boNhoKhongPhinhMai() {
        for (int i = 0; i < 100; i++) {
            limiter.tryAcquire("phien-" + i);
        }
        assertThat(limiter.trackedKeys()).isEqualTo(100);

        clock.advance(120);
        limiter.tryAcquire("phien-moi");

        assertThat(limiter.trackedKeys()).isEqualTo(1);
    }

    @Test
    void retryAfter_lamTronLenTheoGiay_toiThieu1() {
        assertThat(TooManyRequestsException.toSeconds(Duration.ZERO)).isEqualTo(1);
        assertThat(TooManyRequestsException.toSeconds(Duration.ofMillis(1))).isEqualTo(1);
        assertThat(TooManyRequestsException.toSeconds(Duration.ofMillis(1_001))).isEqualTo(2);
        assertThat(TooManyRequestsException.toSeconds(Duration.ofSeconds(30))).isEqualTo(30);
    }

    /** Đồng hồ giả cho test: đứng yên, chỉ nhích khi gọi advance(). */
    private static final class MovableClock extends Clock {
        private Instant now;

        MovableClock(Instant start) {
            this.now = start;
        }

        void advance(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public Instant instant() { return now; }

        @Override
        public ZoneId getZone() { return ZoneId.of("Asia/Ho_Chi_Minh"); }

        @Override
        public Clock withZone(ZoneId zone) { return this; }
    }
}