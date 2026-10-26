package com.ptit.parking.catalog.application.usecase;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import com.ptit.parking.catalog.domain.FeeRule;
import com.ptit.parking.catalog.domain.VehicleType;
import com.ptit.parking.payment.domain.ExchangeRate;

/**
 * Mỗi port chỉ có MỘT phương thức nên lớp giả chỉ là một lambda — không cần CSDL, không cần Spring.
 */
class GetCatalogUseCaseTest {

    private static final FeeRule TOI = new FeeRule(LocalTime.of(18, 0), 5000);
    private static final FeeRule NGAY = new FeeRule(LocalTime.MIDNIGHT, 3000);

    private final GetCatalogUseCase catalog = new GetCatalogUseCase(
            () -> List.of(new VehicleType("XE_MAY", "Xe máy")),
            () -> List.of(TOI, NGAY),                 // CSDL trả về KHÔNG theo thứ tự
            () -> new ExchangeRate(1));

    @Test
    void bangGia_luonSapXepTheoGioBatDauTangDan() {
        assertThat(catalog.feeRules()).containsExactly(NGAY, TOI);
    }

    @Test
    void loaiXe_vaTyLeQuyDoi_traVeNguyenTuKho() {
        assertThat(catalog.vehicleTypes()).containsExactly(new VehicleType("XE_MAY", "Xe máy"));
        assertThat(catalog.exchangeRate()).isEqualTo(new ExchangeRate(1));
    }
}