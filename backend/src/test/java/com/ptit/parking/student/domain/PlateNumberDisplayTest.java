package com.ptit.parking.student.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Đợt 1 — biển số theo quy định: kiểm tra định dạng khi đăng ký mới và dạng hiển thị có dấu. */
class PlateNumberDisplayTest {

    @ParameterizedTest
    @CsvSource({
            "29a-1514,    29A-1514",
            "29A12345,    29A-123.45",
            "29a1-123.45, 29A1-123.45",
            "30LD 1234,   30LD-1234",
            "XD00042,     XD-00042"})
    void hienThiCoDau(String typed, String expected) {
        PlateNumber plate = PlateNumber.of(typed);

        assertThat(plate.isStandard()).isTrue();
        assertThat(plate.display()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABC12345", "29A123", "29A1234567", "2912345", "XD1234"})
    void khongDungQuyDinh(String typed) {
        assertThat(PlateNumber.of(typed).isStandard()).isFalse();
    }

    @Test
    void duLieuCuKhongDungQuyDinh_hienThiNguyenVan() {
        assertThat(PlateNumber.of("ABC12345").display()).isEqualTo("ABC12345");
    }

    @Test
    void maXeDap_theoSoThuTu() {
        assertThat(PlateNumber.bicycleCode(1).value()).isEqualTo("XD00001");
        assertThat(PlateNumber.bicycleCode(1).isBicycleCode()).isTrue();
        assertThatThrownBy(() -> PlateNumber.bicycleCode(100_000)).isInstanceOf(IllegalArgumentException.class);
    }
}
