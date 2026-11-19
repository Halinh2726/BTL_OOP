package com.ptit.parking.gate.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ptit.parking.gate.application.usecase.ReportIncidentUseCase;
import com.ptit.parking.gate.application.usecase.ShiftAttendanceUseCase;
import com.ptit.parking.gate.application.usecase.ShiftHandoverUseCase;
import com.ptit.parking.gate.application.usecase.ViewMyActivityUseCase;
import com.ptit.parking.gate.application.usecase.ViewParkingLotUseCase;
import com.ptit.parking.gate.domain.ParkedVehicle;
import com.ptit.parking.shared.security.SessionUser;
import com.ptit.parking.shared.security.WebSecurityTestConfig;
import com.ptit.parking.student.domain.PlateNumber;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Đợt 2 — định dạng JSON màn hình bảo vệ: không có trường điểm (R06), kiểm tra dữ liệu báo sự cố. */
@WebMvcTest(GateController.class)
@Import(WebSecurityTestConfig.class)
class GateControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ViewParkingLotUseCase parkingLot;

    @MockitoBean
    private ViewMyActivityUseCase myActivity;

    @MockitoBean
    private ShiftHandoverUseCase shifts;

    @MockitoBean
    private ReportIncidentUseCase incidents;

    @MockitoBean
    private ShiftAttendanceUseCase attendance;

    @Test
    void xeTrongBai_bienSoCoDau_danhDauQuaLau_khongCoSoDiem() throws Exception {
        ParkedVehicle vehicle = new ParkedVehicle(5L, PlateNumber.of("29A12345"), "XE_MAY", "B21DCCN001",
                "Nguyễn Văn A", NOW.minusSeconds(25 * 3600), "Trần Văn Bảo");
        when(parkingLot.view(null)).thenReturn(new ViewParkingLotUseCase.LotView(1, 1, List.of(vehicle), NOW));

        mvc.perform(get("/api/v1/guard/parked-vehicles").with(authentication(guard())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].plateDisplay").value("29A-123.45"))
                .andExpect(jsonPath("$.items[0].overdue").value(true))
                .andExpect(jsonPath("$.items[0].minutesParked").value(1500))
                .andExpect(jsonPath("$.items[0].enteredAt").value("2026-11-19T16:00:00+07:00"))
                .andExpect(jsonPath("$.items[0].balance").doesNotExist());
    }

    @Test
    void baoSuCo_thieuLoaiVaMoTa_400_khongGoiUseCase() throws Exception {
        mvc.perform(post("/api/v1/guard/incidents").with(csrf()).with(authentication(guard()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plateNumber":"29A12345","photoContentType":"image/gif"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(incidents);
    }

    @Test
    void loaiSuCoKhongTonTai_400() throws Exception {
        mvc.perform(post("/api/v1/guard/incidents").with(csrf()).with(authentication(guard()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"CHAY_NO","description":"x"}
                                """))
                .andExpect(status().isBadRequest());
        when(incidents.report(any())).thenThrow(new IllegalStateException("không được gọi"));
    }

    private static UsernamePasswordAuthenticationToken guard() {
        SessionUser principal = () -> 4L;
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of(
                new SimpleGrantedAuthority("PARKING_CHECK_IN"), new SimpleGrantedAuthority("PARKING_CHECK_OUT"),
                new SimpleGrantedAuthority("INCIDENT_REPORT")));
    }
}
