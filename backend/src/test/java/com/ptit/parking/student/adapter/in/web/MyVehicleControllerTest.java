package com.ptit.parking.student.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import com.ptit.parking.student.application.usecase.ListMyVehiclesUseCase;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.shared.security.SessionUser;
import com.ptit.parking.shared.security.WebSecurityTestConfig;
import com.ptit.parking.student.application.usecase.RegisterVehicleUseCase;
import com.ptit.parking.student.application.usecase.RegisterVehicleUseCase.Command;
import com.ptit.parking.student.domain.PlateNumber;
import com.ptit.parking.student.domain.Vehicle;
import java.time.Instant;
import java.util.List;
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

/** API #6: chủ xe lấy từ phiên, biển số chuẩn hóa, giờ đăng ký theo giờ Việt Nam, cần CSRF. */
@WebMvcTest(MyVehicleController.class)
@Import(WebSecurityTestConfig.class)
class MyVehicleControllerTest {

    private static final String URL = "/api/v1/me/vehicles";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RegisterVehicleUseCase registerVehicle;

    
    @MockitoBean
    private ListMyVehiclesUseCase listMyVehicles;

    @Test
    void danhSachXe_layTheoNguoiDangNhap_gioVietNam() throws Exception {
        when(listMyVehicles.list(7L)).thenReturn(List.of(
                new Vehicle(101L, 7L, PlateNumber.of("29A33333"), "XE_DAP", Instant.parse("2026-10-03T01:00:00Z")),
                new Vehicle(100L, 7L, PlateNumber.of("29A11111"), "XE_MAY", Instant.parse("2026-10-01T01:00:00Z"))));

        mvc.perform(get(URL).with(authentication(student(7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(101))
                .andExpect(jsonPath("$[0].plateNumber").value("29A33333"))
                .andExpect(jsonPath("$[0].vehicleTypeCode").value("XE_DAP"))
                .andExpect(jsonPath("$[0].registeredAt").value("2026-10-03T08:00:00+07:00"))
                .andExpect(jsonPath("$[1].id").value(100));
    }

    @Test
    void dangKyThanhCong_201_chuXeTuPhien_boQuaStudentIdTrongBody() throws Exception {
        AtomicReference<Command> received = new AtomicReference<>();
        when(registerVehicle.register(any())).thenAnswer(call -> {
            Command c = call.getArgument(0);
            received.set(c);
            return new Vehicle(100L, c.studentId(), PlateNumber.of(c.plateNumber()), c.vehicleTypeCode(),
                    Instant.parse("2026-10-01T01:00:00.123456Z"));
        });

        mvc.perform(post(URL).with(csrf()).with(authentication(student(7L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plateNumber":"29a-123.45","vehicleTypeCode":"XE_MAY","studentId":999}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.plateNumber").value("29A12345"))
                .andExpect(jsonPath("$.vehicleTypeCode").value("XE_MAY"))
                .andExpect(jsonPath("$.registeredAt").value("2026-10-01T08:00:00+07:00"));

        assertThat(received.get().studentId()).isEqualTo(7L);          // KHÔNG phải 999
    }

    @Test
    void bienSoSai_400_taiTruongPlateNumber() throws Exception {
        mvc.perform(post(URL).with(csrf()).with(authentication(student(7L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plateNumber":"29-A1","vehicleTypeCode":"XE_MAY"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields[0].field").value("plateNumber"));
    }

    @Test
    void bienSoDaDangKy_409() throws Exception {
        when(registerVehicle.register(any())).thenThrow(new DomainException(ErrorCode.PLATE_EXISTS));

        mvc.perform(post(URL).with(csrf()).with(authentication(student(7L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plateNumber":"29A12345","vehicleTypeCode":"XE_MAY"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLATE_EXISTS"));
    }

    @Test
    void thieuCsrf_403CsrfInvalid() throws Exception {
        mvc.perform(post(URL).with(authentication(student(7L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plateNumber":"29A12345","vehicleTypeCode":"XE_MAY"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    private static UsernamePasswordAuthenticationToken student(long id) {
        SessionUser principal = () -> id;
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("VEHICLE_REGISTER")));
    }
}