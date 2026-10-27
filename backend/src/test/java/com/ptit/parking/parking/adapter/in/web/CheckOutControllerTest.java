package com.ptit.parking.parking.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ptit.parking.parking.application.port.out.VehicleOwnerDirectory.VehicleOwner;
import com.ptit.parking.parking.application.usecase.CheckOutUseCase;
import com.ptit.parking.parking.application.usecase.CheckOutUseCase.Command;
import com.ptit.parking.parking.application.usecase.PreviewCheckOutUseCase;
import com.ptit.parking.parking.application.usecase.PreviewCheckOutUseCase.Preview;
import com.ptit.parking.parking.domain.ParkingSession;
import com.ptit.parking.payment.domain.ParkingPayment;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.shared.security.SessionUser;
import com.ptit.parking.shared.security.WebSecurityTestConfig;
import com.ptit.parking.student.domain.PlateNumber;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** API #14, #15 — đúng định dạng JSON của đặc tả; Idempotency-Key bắt buộc; bảo vệ cho ra lấy từ phiên. */
@WebMvcTest(CheckOutController.class)
@Import(WebSecurityTestConfig.class)
class CheckOutControllerTest {

    private static final String URL = "/api/v1/guard/check-out";
    private static final UUID KEY = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
    private static final PlateNumber PLATE = new PlateNumber("29A12345");
    private static final VehicleOwner OWNER = new VehicleOwner(100L, PLATE, "XE_MAY", 3L, "B21DCCN001", "Nguyễn Văn A");
    private static final ParkingPayment PAYMENT = ParkingPayment.restore(902L, 5_000, KEY, 4L,
            Instant.parse("2026-10-05T11:30:00.123456Z"), 501L, 7L, 1L, 5_000, 25_000);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PreviewCheckOutUseCase previewCheckOut;

    @MockitoBean
    private CheckOutUseCase checkOut;

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

    @Test
    void xacNhanRa_201_baoVeTuPhien_phiVaGioRaTheoGioVietNam() throws Exception {
        AtomicReference<Command> received = new AtomicReference<>();
        when(checkOut.checkOut(any())).thenAnswer(call -> {
            received.set(call.getArgument(0));
            return new CheckOutUseCase.Result(PAYMENT, false);
        });

        mvc.perform(checkOutRequest("""
                        {"sessionId":501,"fee":1,"receivingWalletId":99}
                        """).header("Idempotency-Key", KEY.toString()))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andExpect(jsonPath("$.transactionId").value(902))
                .andExpect(jsonPath("$.sessionId").value(501))
                .andExpect(jsonPath("$.fee").value(5000))
                .andExpect(jsonPath("$.exitedAt").value("2026-10-05T18:30:00+07:00"))
                .andExpect(jsonPath("$.balanceAfter").value(5000));             // số dư SINH VIÊN, không phải ví nhận

        // Phí, ví nhận trong body bị bỏ qua (R06); bảo vệ lấy từ phiên
        assertThat(received.get()).isEqualTo(new Command(4L, KEY, 501L));
    }

    @Test
    void guiLaiCungKey_201_kemHeaderIdempotentReplayed() throws Exception {
        when(checkOut.checkOut(any())).thenReturn(new CheckOutUseCase.Result(PAYMENT, true));

        mvc.perform(checkOutRequest("""
                        {"sessionId":501}
                        """).header("Idempotency-Key", KEY.toString()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.transactionId").value(902));
    }

    @Test
    void thieuIdempotencyKey_400_khongGoiUseCase() throws Exception {
        mvc.perform(checkOutRequest("""
                        {"sessionId":501}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        verifyNoInteractions(checkOut);
    }

    @Test
    void thieuSessionId_400_VALIDATION_ERROR() throws Exception {
        mvc.perform(checkOutRequest("{}").header("Idempotency-Key", KEY.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(checkOut);
    }

    @Test
    void luotDaThanhToan_409() throws Exception {
        when(checkOut.checkOut(any())).thenThrow(new DomainException(ErrorCode.SESSION_ALREADY_PAID));

        mvc.perform(checkOutRequest("""
                        {"sessionId":501}
                        """).header("Idempotency-Key", KEY.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_ALREADY_PAID"));
    }

    private static MockHttpServletRequestBuilder checkOutRequest(String body) {
        return post(URL).with(csrf()).with(authentication(guard()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static UsernamePasswordAuthenticationToken guard() {
        SessionUser principal = () -> 4L;
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("PARKING_CHECK_OUT")));
    }
}
