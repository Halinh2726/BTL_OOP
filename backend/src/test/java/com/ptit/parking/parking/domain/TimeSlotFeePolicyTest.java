package com.ptit.parking.parking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.catalog.domain.FeeRule;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimeSlotFeePolicyTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final TimeSlotFeePolicy policy = new TimeSlotFeePolicy(
            List.of(new FeeRule(1080, 3000), new FeeRule(1320, 5000), new FeeRule(1440, 15000)),
            VN);

    @Test
    void cungNgay_truoc18h_3k_tu18hDen22h_5k_tu22h_15k() {
        Instant entry = vn("2026-11-23T08:00:00").toInstant();

        FeeQuote before18 = policy.quoteSession(1L, entry, vn("2026-11-23T17:59:59"));
        assertThat(before18.points()).isEqualTo(3000);
        assertThat(before18.overnight()).isFalse();

        FeeQuote at18 = policy.quoteSession(1L, entry, vn("2026-11-23T18:00:00"));
        assertThat(at18.points()).isEqualTo(5000);
        assertThat(at18.overnight()).isFalse();

        FeeQuote at2159 = policy.quoteSession(1L, entry, vn("2026-11-23T21:59:59"));
        assertThat(at2159.points()).isEqualTo(5000);
        assertThat(at2159.overnight()).isFalse();

        FeeQuote at22 = policy.quoteSession(1L, entry, vn("2026-11-23T22:00:00"));
        assertThat(at22.points()).isEqualTo(15000);
        assertThat(at22.overnight()).isTrue();
    }

    @Test
    void quaDem_congDonTungDemVaKhungGioNgayLayXe() {
        // Thứ 2 lúc 09:00
        Instant monEntry = vn("2026-11-23T09:00:00").toInstant();

        // Lấy Thứ 3 lúc 08:30 (1 đêm + trước 18h) -> 15k + 3k = 18k
        FeeQuote tueMorning = policy.quoteSession(1L, monEntry, vn("2026-11-24T08:30:00"));
        assertThat(tueMorning.points()).isEqualTo(18000);
        assertThat(tueMorning.overnight()).isTrue();

        // Lấy Thứ 3 lúc 19:00 (1 đêm + sau 18h) -> 15k + 5k = 20k
        FeeQuote tueEvening = policy.quoteSession(1L, monEntry, vn("2026-11-24T19:00:00"));
        assertThat(tueEvening.points()).isEqualTo(20000);
        assertThat(tueEvening.overnight()).isTrue();

        // Ví dụ của người dùng: gửi từ Thứ 2 đến 19:00 Thứ 4 -> 15k + 15k + 5k = 35k
        FeeQuote wedEvening = policy.quoteSession(1L, monEntry, vn("2026-11-25T19:00:00"));
        assertThat(wedEvening.points()).isEqualTo(35000);
        assertThat(wedEvening.overnight()).isTrue();

        // Gửi Thứ 2 đến 10:00 Thứ 4 -> 15k + 15k + 3k = 33k
        FeeQuote wedMorning = policy.quoteSession(1L, monEntry, vn("2026-11-25T10:00:00"));
        assertThat(wedMorning.points()).isEqualTo(33000);
        assertThat(wedMorning.overnight()).isTrue();
    }

    @Test
    void tuChoiGioRaTruocGioVao() {
        Instant entry = vn("2026-11-23T10:00:00").toInstant();
        assertThatThrownBy(() -> policy.quoteSession(1L, entry, vn("2026-11-23T09:00:00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ZonedDateTime vn(String localIso) {
        return ZonedDateTime.parse(localIso + "+07:00");
    }
}
