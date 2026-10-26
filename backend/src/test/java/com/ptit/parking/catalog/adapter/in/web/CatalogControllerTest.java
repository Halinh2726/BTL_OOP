package com.ptit.parking.catalog.adapter.in.web;

import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.Matchers.aMapWithSize;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ptit.parking.catalog.application.usecase.GetCatalogUseCase;
import com.ptit.parking.catalog.domain.FeeRule;
import com.ptit.parking.catalog.domain.VehicleType;
import com.ptit.parking.payment.domain.ExchangeRate;
import com.ptit.parking.shared.security.SessionUser;
import com.ptit.parking.shared.security.WebSecurityTestConfig;

/** Kiểm tra đúng định dạng JSON của Đặc tả API #9, #10, #11 và quy tắc "chỉ cần đã đăng nhập". */
@WebMvcTest(CatalogController.class)
@Import(WebSecurityTestConfig.class)
class CatalogControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private GetCatalogUseCase catalog;

    @Test
    void loaiXe_traMaVaTen() throws Exception {
        when(catalog.vehicleTypes()).thenReturn(List.of(new VehicleType("XE_MAY", "Xe máy")));

        mvc.perform(get("/api/v1/vehicle-types").with(authentication(guard())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("XE_MAY"))
                .andExpect(jsonPath("$[0].name").value("Xe máy"));
    }

    @Test
    void bangGia_gioDangHHmm() throws Exception {
        when(catalog.feeRules()).thenReturn(List.of(
                new FeeRule(LocalTime.MIDNIGHT, 3000), new FeeRule(LocalTime.of(18, 0), 5000)));

        mvc.perform(get("/api/v1/fee-rules").with(authentication(guard())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].startTime").value("00:00"))
                .andExpect(jsonPath("$[0].feePoints").value(3000))
                .andExpect(jsonPath("$[1].startTime").value("18:00"))
                .andExpect(jsonPath("$[1].feePoints").value(5000));
    }

    @Test
    void tyLeQuyDoi_chiCoMotTruong_khongLoViNhanPhi() throws Exception {
        when(catalog.exchangeRate()).thenReturn(new ExchangeRate(1));

        mvc.perform(get("/api/v1/config/exchange-rate").with(authentication(guard())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashPerPoint").value(1))
                .andExpect(jsonPath("$", aMapWithSize(1)));          // R06
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/vehicle-types", "/api/v1/fee-rules", "/api/v1/config/exchange-rate"})
    void chuaDangNhap_bi401(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    /** Người đã đăng nhập với quyền bất kỳ (ở đây là bảo vệ) — danh mục không đòi mã quyền riêng. */
    private static UsernamePasswordAuthenticationToken guard() {
        SessionUser principal = () -> 4L;
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("PARKING_CHECK_IN")));
    }
}