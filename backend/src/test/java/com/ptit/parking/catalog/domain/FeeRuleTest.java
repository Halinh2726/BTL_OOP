package com.ptit.parking.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class FeeRuleTest {

    @Test
    void phiPhaiLonHon0() {
        assertThatThrownBy(() -> new FeeRule(LocalTime.of(18, 0), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FeeRule(LocalTime.of(18, 0), -5000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void thuTuTuNhien_theoGioBatDau() {
        FeeRule toi = new FeeRule(LocalTime.of(18, 0), 5000);
        FeeRule ngay = new FeeRule(LocalTime.MIDNIGHT, 3000);

        assertThat(List.of(toi, ngay).stream().sorted().toList()).containsExactly(ngay, toi);
    }

    @Test
    void loaiXePhaiCoMaVaTen() {
        assertThatThrownBy(() -> new VehicleType("", "Xe máy")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VehicleType("XE_MAY", null)).isInstanceOf(IllegalArgumentException.class);
    }
}