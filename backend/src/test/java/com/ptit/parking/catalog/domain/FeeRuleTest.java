package com.ptit.parking.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class FeeRuleTest {

    @Test
    void phiPhaiLonHon0() {
        assertThatThrownBy(() -> new FeeRule(360, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FeeRule(360, -5000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void thuTuTuNhien_theoGioBatDau() {
        FeeRule sauGio = new FeeRule(360, 5000);
        FeeRule haiGio = new FeeRule(120, 3000);

        assertThat(List.of(sauGio, haiGio).stream().sorted().toList()).containsExactly(haiGio, sauGio);
    }

    @Test
    void loaiXePhaiCoMaVaTen() {
        assertThatThrownBy(() -> new VehicleType("", "Xe máy")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VehicleType("XE_MAY", null)).isInstanceOf(IllegalArgumentException.class);
    }
}