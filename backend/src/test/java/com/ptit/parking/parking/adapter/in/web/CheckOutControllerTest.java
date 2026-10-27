package com.ptit.parking.parking.adapter.in.web;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.VehicleOwner;
import com.ptit.parking.parking.application.usecase.PreviewCheckOutUseCase;
import com.ptit.parking.parking.application.usecase.PreviewCheckOutUseCase.Preview;
import com.ptit.parking.parking.domain.ParkingSession;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.shared.security.SessionUser;
import com.ptit.parking.shared.security.WebSecurityTestConfig;
import com.ptit.parking.student.domain.PlateNumber;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** API #14 — đúng định dạng JSON của đặc tả, giờ vào theo giờ Việt Nam, kể cả khi không cho ra. */
@WebMvcTest(CheckOutController.class)
@Import(WebSecurityTestConfig.class)
class CheckOutControllerTest {

    private static final String URL = "/api/v1/guard/check-out";
    private static final PlateNumber PLATE = new PlateNumber("29A12345");
    private static final VehicleOwner OWNER = new VehicleOwner(100L, PLATE, "XE_MAY", 3L, "B21DCCN001", "Nguyễn Văn A");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PreviewCheckOutUseCase previewCheckOut;

    @Test
    void choRa_traDuThongTin_gioVaoTheoGioVietNam() throws Exception {
        ParkingSession session = ParkingSession.open(100L, 4L, Instant.parse("2026-10-05T00:55:00.654321Z")).withId(501L);
        when(previewCheckOut.preview("29a-123.45")).thenReturn(new Preview(OWNER, session, 10_000L, 5_000L, true, null));

        mvc.perform(get(URL + "/preview").param("plate", "29a-123.45").with(authentication(guard())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(501))
                .andExpect(jsonPath("$.plateNumber").value("29A12345"))
                .andExpect(jsonPath("$.vehicleTypeCode").value("XE_MAY"))
                .andExpect(jsonPath("$.studentCode").value("B21DCCN001"))
                .andExpect(jsonPath("$.fullName").value("Nguyễn Văn A"))
                .andExpect(jsonPath("$.enteredAt").value("2026-10-05T07:55:00+07:00"))
                .andExpect(jsonPath("$.balance").value(10000))
                .andExpect(jsonPath("$.fee").value(5000))
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.reason").value(nullValue()));
    }

    @Test
    void khongCoLuotDangMo_van200_luotVaPhiLaNull() throws Exception {
        when(previewCheckOut.preview("29A12345")).thenReturn(new Preview(OWNER, null, 10_000L, null, false,
                ErrorCode.NO_OPEN_SESSION));

        mvc.perform(get(URL + "/preview").param("plate", "29A12345").with(authentication(guard())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(nullValue()))
                .andExpect(jsonPath("$.enteredAt").value(nullValue()))
                .andExpect(jsonPath("$.fee").value(nullValue()))
                .andExpect(jsonPath("$.studentCode").value("B21DCCN001"))
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.reason").value("NO_OPEN_SESSION"));
    }

    @Test
    void thieuThamSoPlate_400() throws Exception {
        mvc.perform(get(URL + "/preview").with(authentication(guard())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private static UsernamePasswordAuthenticationToken guard() {
        SessionUser principal = () -> 4L;
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("PARKING_CHECK_OUT")));
    }
}