package com.ptit.parking.student.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PlateNumberTest {

    @ParameterizedTest
    @ValueSource(strings = {"29A12345", "29a12345", "29A-123.45", " 29a 123 45 ", "29-a1.23-45"})
    void moiCachViet_deuLaCungMotBienSo(String raw) {
        assertThat(PlateNumber.of(raw)).isEqualTo(new PlateNumber("29A12345"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"29A1", "29Đ12345", "29A_12345", "123456789012345678901"})
    void sauChuanHoaVanSaiDinhDang_thiKhongTaoDuoc(String raw) {
        assertThatThrownBy(() -> PlateNumber.of(raw)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void null_khongTaoDuoc() {
        assertThatThrownBy(() -> PlateNumber.of(null)).isInstanceOf(IllegalArgumentException.class);
    }
}