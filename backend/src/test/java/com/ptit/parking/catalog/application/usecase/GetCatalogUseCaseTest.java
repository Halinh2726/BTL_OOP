package com.ptit.parking.catalog.application.usecase;

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

    private static final FeeRule SAU_GIO = new FeeRule(360, 5000);
    private static final FeeRule HAI_GIO = new FeeRule(120, 3000);

    private final GetCatalogUseCase catalog = new GetCatalogUseCase(
            () -> List.of(new VehicleType("XE_MAY", "Xe máy")),
            () -> List.of(SAU_GIO, HAI_GIO),                 // CSDL trả về KHÔNG theo thứ tự
            () -> new ExchangeRate(1));

    @Test
    void bangGia_luonSapXepTheoCanTrenTangDan() {
        assertThat(catalog.feeRules()).containsExactly(HAI_GIO, SAU_GIO);
    }

    @Test
    void loaiXe_vaTyLeQuyDoi_traVeNguyenTuKho() {
        assertThat(catalog.vehicleTypes()).containsExactly(new VehicleType("XE_MAY", "Xe máy"));
        assertThat(catalog.exchangeRate()).isEqualTo(new ExchangeRate(1));
    }
}