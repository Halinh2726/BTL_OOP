package com.ptit.parking.parking.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.ptit.parking.catalog.domain.FeeRule;
import com.ptit.parking.parking.application.port.out.PassCoverage;
import com.ptit.parking.parking.domain.FeePolicy;
import com.ptit.parking.parking.domain.FeeQuote;
import com.ptit.parking.parking.domain.TimeSlotFeePolicy;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Đợt 5 — vé tháng theo mẫu Decorator: bọc bảng giá theo giờ, không sửa lớp bên trong. */
class PassAwareFeePolicyTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final long WITH_PASS = 1L;
    private static final long NO_PASS = 2L;

    private final FeePolicy hourly = new TimeSlotFeePolicy(
            List.of(new FeeRule(LocalTime.MIDNIGHT, 3000), new FeeRule(LocalTime.of(18, 0), 5000)), VN);
    /** Xe 1 có vé tháng 11/2026; xe 2 không có vé. */
    private final PassCoverage passes = (vehicleId, day) ->
            vehicleId == WITH_PASS && day.getMonthValue() == 11 && day.getYear() == 2026
                    ? Optional.of(LocalDate.of(2026, 11, 30)) : Optional.empty();
    private final FeePolicy policy = new PassAwareFeePolicy(hourly, passes, VN);

    @Test
    void coVeThang_mienPhi_kemHanVe() {
        FeeQuote quote = policy.quote(WITH_PASS, ZonedDateTime.parse("2026-11-20T19:00:00+07:00"));
        assertThat(quote.isFree()).isTrue();
        assertThat(quote.passValidUntil()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(policy.minimumFeeFor(WITH_PASS, ZonedDateTime.parse("2026-11-20T07:00:00+07:00"))).isZero();
    }

    @Test
    void khongCoVe_hoacVeHetThang_tinhTheoBangGiaNhuCu() {
        assertThat(policy.quote(NO_PASS, ZonedDateTime.parse("2026-11-20T19:00:00+07:00")))
                .isEqualTo(FeeQuote.charge(5000));
        // 23:30 ngày 30/11 giờ VN vẫn trong tháng 11 (dù đã là 1/12... theo UTC thì chưa): vẫn miễn phí
        assertThat(policy.quote(WITH_PASS, ZonedDateTime.parse("2026-11-30T16:30:00Z")).isFree()).isTrue();
        // 00:30 ngày 1/12 giờ VN (17:30 UTC ngày 30/11): vé tháng 11 đã hết
        assertThat(policy.quote(WITH_PASS, ZonedDateTime.parse("2026-11-30T17:30:00Z")).points()).isEqualTo(3000);
        assertThat(policy.minimumFeeFor(NO_PASS, ZonedDateTime.parse("2026-11-20T07:00:00+07:00"))).isEqualTo(3000);
    }

    @Test
    void phuongThucCu_khongDoi_uyQuyenChoChinhSachBenTrong() {
        assertThat(policy.feeAt(ZonedDateTime.parse("2026-11-20T19:00:00+07:00"))).isEqualTo(5000);
        assertThat(policy.minimumFee()).isEqualTo(3000);
        // Chính sách cũ (không bọc) vẫn có quote/minimumFeeFor mặc định theo từng xe
        assertThat(hourly.quote(WITH_PASS, ZonedDateTime.parse("2026-11-20T10:00:00+07:00"))).isEqualTo(FeeQuote.charge(3000));
    }
}
