package com.ptit.parking.parking.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.ptit.parking.catalog.domain.FeeRule;
import com.ptit.parking.parking.application.port.out.PassCoverage;
import com.ptit.parking.parking.domain.DurationFeePolicy;
import com.ptit.parking.parking.domain.FeePolicy;
import com.ptit.parking.parking.domain.FeeQuote;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PassAwareFeePolicyTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final long WITH_PASS = 1L;
    private static final long NO_PASS = 2L;
    private static final LocalDate VALID_UNTIL = LocalDate.of(2026, 11, 30);

    private final FeePolicy durationPolicy = new DurationFeePolicy(
            List.of(new FeeRule(120, 3000), new FeeRule(360, 5000), new FeeRule(1440, 7000)));
    private final PassCoverage passes = (vehicleId, day) ->
            vehicleId == WITH_PASS && day.getMonthValue() == 11 && day.getYear() == 2026
                    ? Optional.of(VALID_UNTIL) : Optional.empty();
    private final FeePolicy policy = new PassAwareFeePolicy(durationPolicy, passes, VN);

    @Test
    void coVeThang_mienPhi_kemHanVe() {
        ZonedDateTime exit = ZonedDateTime.parse("2026-11-20T19:00:00+07:00");
        FeeQuote quote = policy.quote(WITH_PASS, exit.minusHours(3).toInstant(), exit);

        assertThat(quote.isFree()).isTrue();
        assertThat(quote.passValidUntil()).isEqualTo(VALID_UNTIL);
        assertThat(policy.minimumFeeFor(WITH_PASS, exit)).isZero();
    }

    @Test
    void khongCoVe_hoacVeHetThang_tinhTheoThoiLuong() {
        Instant enteredAt = Instant.parse("2026-11-30T14:30:00Z");
        ZonedDateTime beforeMidnight = ZonedDateTime.parse("2026-11-30T16:30:00Z");
        ZonedDateTime afterMidnight = ZonedDateTime.parse("2026-11-30T17:30:00Z");

        assertThat(policy.quote(NO_PASS, enteredAt, afterMidnight)).isEqualTo(FeeQuote.charge(5000));
        assertThat(policy.quote(WITH_PASS, enteredAt, beforeMidnight).isFree()).isTrue();
        assertThat(policy.quote(WITH_PASS, enteredAt, afterMidnight)).isEqualTo(FeeQuote.charge(5000));
        assertThat(policy.minimumFeeFor(NO_PASS, beforeMidnight)).isEqualTo(3000);
    }

    @Test
    void phiTheoThoiLuongUyQuyenChoChinhSachBenTrong() {
        assertThat(policy.feeFor(Duration.ofHours(3))).isEqualTo(5000);
        assertThat(policy.minimumFee()).isEqualTo(3000);
    }
}
