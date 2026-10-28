package com.ptit.parking.parking.adapter.in.web;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.VehicleOwner;
import com.ptit.parking.parking.application.usecase.CheckInUseCase;
import com.ptit.parking.parking.application.usecase.CheckInUseCase.Command;
import com.ptit.parking.parking.application.usecase.PreviewCheckInUseCase;
import com.ptit.parking.parking.application.usecase.PreviewCheckInUseCase.Preview;
import com.ptit.parking.parking.domain.ParkingSession;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.shared.security.SessionUser;
import com.ptit.parking.shared.security.WebSecurityTestConfig;
import com.ptit.parking.student.domain.PlateNumber;

/** API #12, #13 — đúng định dạng JSON của đặc tả; bảo vệ cho vào lấy từ phiên. */
@WebMvcTest(CheckInController.class)
@Import(WebSecurityTestConfig.class)
class CheckInControllerTest {

    private static final String URL = "/api/v1/guard/check-in";
    private static final PlateNumber PLATE = new PlateNumber("29A12345");
    private static final VehicleOwner OWNER = new VehicleOwner(100L, PLATE, "XE_MAY", 3L, "B21DCCN001", "Nguyễn Văn A");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PreviewCheckInUseCase previewCheckIn;

    @MockitoBean
    private CheckInUseCase checkIn;

    @Test
    void choVao_traDuThongTin() throws Exception {
        when(previewCheckIn.preview("29a-123.45")).thenReturn(new Preview(PLATE, OWNER, 10_000L, 3_000, true, null));

        mvc.perform(get(URL + "/preview").param("plate", "29a-123.45").with(authentication(guard(4L))))
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

        mvc.perform(get(URL + "/preview").param("plate", "30B99999").with(authentication(guard(4L))))
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
        mvc.perform(get(URL + "/preview").with(authentication(guard(4L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void xacNhanChoVao_201_baoVeTuPhien_bienSoChuanHoa_gioVietNam() throws Exception {
        AtomicReference<Command> received = new AtomicReference<>();
        when(checkIn.checkIn(any())).thenAnswer(call -> {
            received.set(call.getArgument(0));
            ParkingSession session = ParkingSession.open(100L, 4L, Instant.parse("2026-10-05T00:55:00.654321Z"))
                    .withId(501L);
            return new CheckInUseCase.Result(session, OWNER);
        });

        mvc.perform(post(URL).with(csrf()).with(authentication(guard(4L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plateNumber":"29a-123.45"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value(501))
                .andExpect(jsonPath("$.plateNumber").value("29A12345"))
                .andExpect(jsonPath("$.enteredAt").value("2026-10-05T07:55:00+07:00"));

        assertThat(received.get()).isEqualTo(new Command(4L, "29A12345"));
    }

    @Test
    void xeDangGui_409_SESSION_ALREADY_OPEN() throws Exception {
        when(checkIn.checkIn(any())).thenThrow(new DomainException(ErrorCode.SESSION_ALREADY_OPEN));

        mvc.perform(post(URL).with(csrf()).with(authentication(guard(4L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plateNumber":"29A12345"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_ALREADY_OPEN"));
    }

    @Test
    void bienSoSaiDinhDang_400_khongGoiUseCase() throws Exception {
        mvc.perform(post(URL).with(csrf()).with(authentication(guard(4L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plateNumber":"29-A1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(checkIn);
    }

    private static UsernamePasswordAuthenticationToken guard(long id) {
        SessionUser principal = () -> id;
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("PARKING_CHECK_IN")));
    }
}