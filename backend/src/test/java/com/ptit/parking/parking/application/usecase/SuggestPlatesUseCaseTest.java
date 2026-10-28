package com.ptit.parking.parking.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ptit.parking.parking.application.port.out.InMemoryVehicleOwnerDirectory;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.PlateSuggestion;
import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.VehicleOwner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.student.domain.PlateNumber;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** BV01, BV02 — gợi ý biển số khi bảo vệ chỉ gõ vài ký tự. */
class SuggestPlatesUseCaseTest {

    private final InMemoryVehicleOwnerDirectory vehicles = new InMemoryVehicleOwnerDirectory();
    private final SuggestPlatesUseCase suggestPlates = new SuggestPlatesUseCase(vehicles);

    private long nextVehicleId = 1;

    private void vehicle(String plate) {
        long id = nextVehicleId++;
        vehicles.add(new VehicleOwner(id, new PlateNumber(plate), "XE_MAY", 100 + id, "SV" + id, "Sinh viên " + id));
    }

    private List<String> plates(List<PlateSuggestion> suggestions) {
        return suggestions.stream().map(s -> s.plate().value()).toList();
    }

    @Test
    void goMaySoCuoi_bienKetThucBangDoXepTruoc() {
        vehicle("29A12345");
        vehicle("30B23456");
        vehicle("29A23499");
        vehicle("51F11111");

        List<PlateSuggestion> result = suggestPlates.suggest("234", false);

        // cả ba biển đều chứa "234" ở giữa, không biển nào kết thúc bằng "234" -> xếp theo biển số
        assertThat(plates(result)).containsExactly("29A12345", "29A23499", "30B23456");
        assertThat(plates(suggestPlates.suggest("45", false))).containsExactly("29A12345", "30B23456"); // kết thúc trước
        assertThat(plates(suggestPlates.suggest("99", false))).containsExactly("29A23499");
    }

    @Test
    void bienKetThucBangDoanGo_luonDungTruocBienChiChua() {
        vehicle("12345678");    // chứa "345" ở giữa
        vehicle("99Z00345");    // kết thúc bằng "345"

        assertThat(plates(suggestPlates.suggest("345", false))).containsExactly("99Z00345", "12345678");
    }

    @Test
    void goKemDauVaChuThuong_vanChuanHoaNhuBienSo() {
        vehicle("29A12345");

        assertThat(plates(suggestPlates.suggest(" 123.4-5 ", false))).containsExactly("29A12345");
        assertThat(plates(suggestPlates.suggest("a1", false))).containsExactly("29A12345");
    }

    @Test
    void xeRa_chiGoiYXeDangTrongBai_kemCoParked() {
        vehicle("29A12345");
        vehicle("30B12345");
        vehicles.markParked(new PlateNumber("30B12345"));

        List<PlateSuggestion> all = suggestPlates.suggest("12345", false);
        assertThat(all).extracting(PlateSuggestion::parked).containsExactly(false, true);

        assertThat(plates(suggestPlates.suggest("12345", true))).containsExactly("30B12345");
    }

    @Test
    void toiDa8GoiY() {
        for (int i = 10; i < 30; i++) {
            vehicle("29A123" + i);
        }

        assertThat(suggestPlates.suggest("123", false)).hasSize(SuggestPlatesUseCase.MAX_SUGGESTIONS);
    }

    @Test
    void khongKhopXeNao_traDanhSachRong() {
        vehicle("29A12345");

        assertThat(suggestPlates.suggest("999", false)).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "1", " - ", "12%", "29A_1", "123456789012345678901"})
    void quaNganHoacCoKyTuLa_VALIDATION_ERROR(String fragment) {
        assertThatThrownBy(() -> suggestPlates.suggest(fragment, false))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }
}
