package com.ptit.parking.parking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.catalog.domain.FeeRule;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** R03 — kiểm tra đúng các MỐC BIÊN, nơi tính sai phí dễ xảy ra nhất. */
class TimeSlotFeePolicyTest {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final List<FeeRule> R03 = List.of(
            new FeeRule(LocalTime.MIDNIGHT, 3000),
            new FeeRule(LocalTime.of(18, 0), 5000));

    private final FeePolicy policy = new TimeSlotFeePolicy(R03, VIETNAM);

    @ParameterizedTest(name = "ra lúc {0} → {1} điểm")
    @CsvSource({
            "2026-10-27T00:00:00+07:00, 3000",     // đầu ngày
            "2026-10-27T08:00:00+07:00, 3000",
            "2026-10-27T17:59:59+07:00, 3000",     // 1 giây trước mốc
            "2026-10-27T18:00:00+07:00, 5000",     // đúng mốc: "từ 18:00 trở đi"
            "2026-10-27T23:59:59+07:00, 5000",     // cuối ngày
    })
    void phiTheoGioRa_gioVietNam(String exitTime, long expectedFee) {
        assertThat(policy.feeAt(ZonedDateTime.parse(exitTime))).isEqualTo(expectedFee);
    }

    @ParameterizedTest(name = "ra lúc {0} (UTC) → {1} điểm")
    @CsvSource({
            "2026-10-27T10:59:59Z, 3000",          // = 17:59:59 giờ Việt Nam
            "2026-10-27T11:00:00Z, 5000",          // = 18:00:00 giờ Việt Nam
            "2026-10-27T17:30:00Z, 3000",          // = 00:30 NGÀY HÔM SAU ở Việt Nam
    })
    void thoiDiemTheoMuiGioKhac_vanTinhTheoGioVietNam(String exitTime, long expectedFee) {
        assertThat(policy.feeAt(ZonedDateTime.parse(exitTime))).isEqualTo(expectedFee);
    }

    @Test
    void phiThapNhat_laMucKiemTraKhiXeVao() {
        assertThat(policy.minimumFee()).isEqualTo(3000);
    }

    @Test
    void bangGiaLaDuLieu_themKhungMoiKhongCanSuaCode() {
        FeePolicy withLateNight = new TimeSlotFeePolicy(List.of(
                new FeeRule(LocalTime.of(22, 0), 7000),           // cố ý đưa vào KHÔNG theo thứ tự
                new FeeRule(LocalTime.MIDNIGHT, 3000),
                new FeeRule(LocalTime.of(18, 0), 5000)), VIETNAM);

        assertThat(withLateNight.feeAt(ZonedDateTime.parse("2026-10-27T21:59:59+07:00"))).isEqualTo(5000);
        assertThat(withLateNight.feeAt(ZonedDateTime.parse("2026-10-27T22:30:00+07:00"))).isEqualTo(7000);
        assertThat(withLateNight.minimumFee()).isEqualTo(3000);
    }

    @Test
    void bangGiaThieuKhung0h_khongTaoDuoc() {
        assertThatThrownBy(() -> new TimeSlotFeePolicy(List.of(new FeeRule(LocalTime.of(6, 0), 3000)), VIETNAM))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TimeSlotFeePolicy(List.of(), VIETNAM))
                .isInstanceOf(IllegalArgumentException.class);
    }
}