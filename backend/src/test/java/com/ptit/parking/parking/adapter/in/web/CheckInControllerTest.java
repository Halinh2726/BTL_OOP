package com.ptit.parking.parking.adapter.in.web;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.VehicleOwner;
import com.ptit.parking.parking.application.usecase.PreviewCheckInUseCase;
import com.ptit.parking.parking.application.usecase.PreviewCheckInUseCase.Preview;
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

/** API #12 — đúng định dạng JSON của đặc tả, kể cả khi không cho vào. */
@WebMvcTest(CheckInController.class)
@Import(WebSecurityTestConfig.class)
class CheckInControllerTest {

    private static final String URL = "/api/v1/guard/check-in/preview";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PreviewCheckInUseCase previewCheckIn;

    @Test
    void choVao_traDuThongTin() throws Exception {
        PlateNumber plate = new PlateNumber("29A12345");
        when(previewCheckIn.preview("29a-123.45")).thenReturn(new Preview(plate,
                new VehicleOwner(100L, plate, "XE_MAY", 3L, "B21DCCN001", "Nguyễn Văn A"),
                10_000L, 3_000, true, null));

        mvc.perform(get(URL).param("plate", "29a-123.45").with(authentication(guard())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plateNumber").value("29A12345"))
                .andExpect(jsonPath("$.vehicleTypeCode").value("XE_MAY"))
                .andExpect(jsonPath("$.studentCode").value("B21DCCN001"))
                .andExpect(jsonPath("$.fullName").value("Nguyễn Văn A"))
                .andExpect(jsonPath("$.balance").value(10000))
                .andExpect(jsonPath("$.minFee").value(3000))
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.reason").value(nullValue()));
    }

    @Test
    void bienSoChuaDangKy_van200_allowedFalse_kemLyDo() throws Exception {
        when(previewCheckIn.preview("30B99999")).thenReturn(new Preview(new PlateNumber("30B99999"),
                null, null, 3_000, false, ErrorCode.PLATE_NOT_FOUND));

        mvc.perform(get(URL).param("plate", "30B99999").with(authentication(guard())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plateNumber").value(nullValue()))
                .andExpect(jsonPath("$.studentCode").value(nullValue()))
                .andExpect(jsonPath("$.balance").value(nullValue()))
                .andExpect(jsonPath("$.minFee").value(3000))
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.reason").value("PLATE_NOT_FOUND"));
    }

    @Test
    void thieuThamSoPlate_400() throws Exception {
        mvc.perform(get(URL).with(authentication(guard())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private static UsernamePasswordAuthenticationToken guard() {
        SessionUser principal = () -> 4L;
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("PARKING_CHECK_IN")));
    }
}