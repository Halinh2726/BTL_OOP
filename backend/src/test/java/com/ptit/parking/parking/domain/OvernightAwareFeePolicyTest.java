package com.ptit.parking.parking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.ptit.parking.catalog.domain.FeeRule;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class OvernightAwareFeePolicyTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final TimeSlotFeePolicy base = new TimeSlotFeePolicy(List.of(
            new FeeRule(LocalTime.MIDNIGHT, 3_000),
            new FeeRule(LocalTime.of(18, 0), 5_000)), VN);

    @Test
    void khoaLayXeTu20h_vaChoPhepTruoc20h() {
        ParkingPolicyConfig config = new ParkingPolicyConfig(LocalTime.of(20, 0), 15_000L, true);
        OvernightAwareFeePolicy policy = new OvernightAwareFeePolicy(base, () -> config, VN);

        ZonedDateTime at1959 = ZonedDateTime.of(2026, 11, 22, 19, 59, 59, 0, VN);
        ZonedDateTime at2000 = ZonedDateTime.of(2026, 11, 22, 20, 0, 0, 0, VN);

        assertThat(policy.isCheckoutLocked(at1959)).isFalse();
        assertThat(policy.isCheckoutLocked(at2000)).isTrue();
    }

    @Test
    void tatGioKhoa_thiSau20hVanChoRa() {
        ParkingPolicyConfig config = new ParkingPolicyConfig(LocalTime.of(20, 0), 15_000L, false);
        OvernightAwareFeePolicy policy = new OvernightAwareFeePolicy(base, () -> config, VN);

        ZonedDateTime at2130 = ZonedDateTime.of(2026, 11, 22, 21, 30, 0, 0, VN);
        assertThat(policy.isCheckoutLocked(at2130)).isFalse();
    }

    @Test
    void guiTrongNgay_tinhTheoBangPhiThuong() {
        OvernightAwareFeePolicy policy = new OvernightAwareFeePolicy(base, () -> ParkingPolicyConfig.DEFAULT, VN);

        Instant entered = ZonedDateTime.of(2026, 11, 22, 8, 0, 0, 0, VN).toInstant();
        ZonedDateTime exitBefore18 = ZonedDateTime.of(2026, 11, 22, 17, 30, 0, 0, VN);
        ZonedDateTime exitAfter18 = ZonedDateTime.of(2026, 11, 22, 18, 30, 0, 0, VN);

        FeeQuote quote1 = policy.quoteSession(1L, entered, exitBefore18);
        FeeQuote quote2 = policy.quoteSession(1L, entered, exitAfter18);

        assertThat(quote1.points()).isEqualTo(3_000);
        assertThat(quote1.overnight()).isFalse();
        assertThat(quote2.points()).isEqualTo(5_000);
        assertThat(quote2.overnight()).isFalse();
    }

    @Test
    void guiQuaDem_tinh15000TongCong_khongCongDonPhiThuong() {
        OvernightAwareFeePolicy policy = new OvernightAwareFeePolicy(base, () -> ParkingPolicyConfig.DEFAULT, VN);

        Instant entered = ZonedDateTime.of(2026, 11, 22, 17, 0, 0, 0, VN).toInstant();
        ZonedDateTime exitNextMorning = ZonedDateTime.of(2026, 11, 23, 8, 15, 0, 0, VN);

        FeeQuote quote = policy.quoteSession(1L, entered, exitNextMorning);

        assertThat(quote.points()).isEqualTo(15_000);
        assertThat(quote.overnight()).isTrue();
    }

    @Test
    void xeCoVeThang_vanMienPhiDuQuaDem() {
        FeePolicy passFree = new FeePolicy() {
            @Override
            public long feeAt(ZonedDateTime exitTime) { return 5_000; }
            @Override
            public long minimumFee() { return 3_000; }
            @Override
            public FeeQuote quote(long vehicleId, ZonedDateTime exitTime) {
                return FeeQuote.coveredByPass(LocalDate.of(2026, 11, 30));
            }
        };
        OvernightAwareFeePolicy policy = new OvernightAwareFeePolicy(passFree, () -> ParkingPolicyConfig.DEFAULT, VN);

        Instant entered = ZonedDateTime.of(2026, 11, 22, 17, 0, 0, 0, VN).toInstant();
        ZonedDateTime exitNextMorning = ZonedDateTime.of(2026, 11, 23, 8, 15, 0, 0, VN);

        FeeQuote quote = policy.quoteSession(1L, entered, exitNextMorning);
        assertThat(quote.isFree()).isTrue();
        assertThat(quote.overnight()).isFalse();
    }
}
