package com.ptit.parking.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Đợt 4a — hồ sơ lượt xe theo giờ: điền đủ 24 giờ, trung bình, giờ cao điểm. */
class TrafficProfileTest {

    /** 10 ngày: 7 giờ có 120 lượt vào, 12 giờ có 40, 17 giờ có 120 (bằng 7 giờ), 17 giờ có 150 lượt ra. */
    private final TrafficProfile profile = TrafficProfile.of(10,
            Map.of(7, 120L, 12, 40L, 17, 120L), Map.of(11, 60L, 17, 150L));

    @Test
    void dienDu24Gio_gioKhongCoXeLa0() {
        assertThat(profile.entries()).hasSize(24);
        assertThat(profile.entries().get(0)).isZero();
        assertThat(profile.exits().get(17)).isEqualTo(150L);
    }

    @Test
    void trungBinh_theoSoNgay() {
        assertThat(profile.totalEntries()).isEqualTo(280);
        assertThat(profile.totalExits()).isEqualTo(210);
        assertThat(profile.averageEntriesPerDay()).isEqualTo(28.0);
        assertThat(profile.averageEntriesAt(7)).isEqualTo(12.0);
        assertThat(profile.averageExitsAt(17)).isEqualTo(15.0);
    }

    @Test
    void gioCaoDiem_nhieuXeNhatTruoc_bangNhauThiGioSomTruoc_boQuaGioKhongCoXe() {
        assertThat(profile.peakHours(3)).containsExactly(7, 17, 12);
        assertThat(profile.peakHours(10)).containsExactly(7, 17, 12);
        assertThat(TrafficProfile.of(1, Map.of(), Map.of()).peakHours(3)).isEmpty();
    }

    @Test
    void duLieuKhongHopLe_biChan() {
        assertThatThrownBy(() -> TrafficProfile.of(0, Map.of(), Map.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TrafficProfile(1, List.of(1L), List.of(1L))).isInstanceOf(IllegalArgumentException.class);
    }
}
