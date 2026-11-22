package com.ptit.parking.parking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.catalog.domain.FeeRule;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DurationFeePolicyTest {

    private final FeePolicy policy = new DurationFeePolicy(List.of(
            new FeeRule(1440, 7000), new FeeRule(120, 3000), new FeeRule(360, 5000)));

    @ParameterizedTest
    @CsvSource({
            "PT0S, 3000",
            "PT2H, 3000",
            "PT2H0.000001S, 5000",
            "PT6H, 5000",
            "PT6H0.000001S, 7000",
            "PT24H, 7000",
            "PT24H0.000001S, 10000",
            "PT26H, 10000",
            "PT26H0.000001S, 12000",
            "PT48H, 14000",
            "PT49H, 17000"
    })
    void tinhPhiTheoMocThoiLuong(String duration, long fee) {
        assertThat(policy.feeFor(Duration.parse(duration))).isEqualTo(fee);
    }

    @Test
    void quaNuaDemVanTinhTheoThoiLuongThuc() {
        Instant enteredAt = Instant.parse("2026-11-22T16:30:00Z");
        ZonedDateTime exit = ZonedDateTime.parse("2026-11-23T00:30:00+07:00");

        assertThat(policy.quote(1L, enteredAt, exit)).isEqualTo(FeeQuote.charge(3000));
        assertThat(policy.minimumFee()).isEqualTo(3000);
    }

    @Test
    void tuChoiThoiLuongAmVaBangGiaThieuMocCuoi() {
        assertThatThrownBy(() -> policy.feeFor(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DurationFeePolicy(List.of(new FeeRule(120, 3000))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tuChoiMocTrungHoacPhiGiam() {
        assertThatThrownBy(() -> new DurationFeePolicy(List.of(
                new FeeRule(120, 3000), new FeeRule(120, 5000), new FeeRule(1440, 7000))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DurationFeePolicy(List.of(new FeeRule(120, 5000), new FeeRule(1440, 3000))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
