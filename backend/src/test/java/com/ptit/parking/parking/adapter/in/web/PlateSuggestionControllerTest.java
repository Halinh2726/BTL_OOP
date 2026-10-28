package com.ptit.parking.parking.adapter.in.web;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.PlateSuggestion;
import com.ptit.parking.parking.application.usecase.SuggestPlatesUseCase;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.shared.security.SessionUser;
import com.ptit.parking.shared.security.WebSecurityTestConfig;
import com.ptit.parking.student.domain.PlateNumber;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** API #24 — gợi ý biển số: đúng định dạng JSON, KHÔNG lộ thông tin chủ xe. */
@WebMvcTest(PlateSuggestionController.class)
@Import(WebSecurityTestConfig.class)
class PlateSuggestionControllerTest {

    private static final String URL = "/api/v1/guard/plates";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SuggestPlatesUseCase suggestPlates;

    @Test
    void traBienSo_loaiXe_dangGui_khongCoTenHayMssv() throws Exception {
        when(suggestPlates.suggest("2345", false)).thenReturn(List.of(
                new PlateSuggestion(new PlateNumber("29A12345"), "XE_MAY", true),
                new PlateSuggestion(new PlateNumber("30B92345"), "XE_DAP", false)));

        mvc.perform(get(URL).param("q", "2345").with(authentication(guard("PARKING_CHECK_IN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].plateNumber").value("29A12345"))
                .andExpect(jsonPath("$[0].vehicleTypeCode").value("XE_MAY"))
                .andExpect(jsonPath("$[0].parked").value(true))
                .andExpect(jsonPath("$[1].parked").value(false))
                .andExpect(jsonPath("$[0].fullName").doesNotExist())
                .andExpect(jsonPath("$[0].studentCode").doesNotExist());
    }

    @Test
    void parkedTrue_chuyenXuongUseCase() throws Exception {
        when(suggestPlates.suggest("345", true)).thenReturn(List.of());

        mvc.perform(get(URL).param("q", "345").param("parked", "true").with(authentication(guard("PARKING_CHECK_OUT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void thieuThamSoQ_400() throws Exception {
        mvc.perform(get(URL).with(authentication(guard("PARKING_CHECK_IN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void doanGoKhongHopLe_400() throws Exception {
        when(suggestPlates.suggest("1", false)).thenThrow(new DomainException(ErrorCode.VALIDATION_ERROR));

        mvc.perform(get(URL).param("q", "1").with(authentication(guard("PARKING_CHECK_IN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private static UsernamePasswordAuthenticationToken guard(String permission) {
        SessionUser principal = () -> 4L;
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority(permission)));
    }
}
