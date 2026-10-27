package com.ptit.parking.manager.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ptit.parking.auth.domain.AccountStatus;
import com.ptit.parking.auth.domain.Guard;
import com.ptit.parking.manager.application.usecase.CreateGuardUseCase;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.shared.security.SessionUser;
import com.ptit.parking.shared.security.WebSecurityTestConfig;
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

/** API #21 — tạo tài khoản bảo vệ. */
@WebMvcTest(GuardController.class)
@Import(WebSecurityTestConfig.class)
class GuardControllerTest {

    private static final String URL = "/api/v1/manager/guards";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CreateGuardUseCase createGuard;

    @Test
    void taoThanhCong_201_khongTraMatKhau() throws Exception {
        when(createGuard.create(new CreateGuardUseCase.Command("Ngô Văn H", "0912345678", "baove03", "MatKhau@123")))
                .thenReturn(new Guard(6L, "baove03", "$2a$12$hash", "Ngô Văn H", AccountStatus.HOAT_DONG, "0912345678"));

        mvc.perform(post(URL).with(csrf()).with(authentication(manager()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":" Ngô Văn H ","phone":"0912345678","username":"baove03","password":"MatKhau@123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(6))
                .andExpect(jsonPath("$.username").value("baove03"))
                .andExpect(jsonPath("$.fullName").value("Ngô Văn H"))
                .andExpect(jsonPath("$.phone").value("0912345678"))
                .andExpect(jsonPath("$.status").value("HOAT_DONG"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void duLieuSai_400_tungTruong_khongGoiUseCase() throws Exception {
        mvc.perform(post(URL).with(csrf()).with(authentication(manager()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"","phone":"912345678","username":"BV","password":"123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields[?(@.field == 'fullName')]").exists())
                .andExpect(jsonPath("$.fields[?(@.field == 'phone')]").exists())
                .andExpect(jsonPath("$.fields[?(@.field == 'username')]").exists())
                .andExpect(jsonPath("$.fields[?(@.field == 'password')]").exists());

        verify(createGuard, never()).create(any());
    }

    @Test
    void trungTenDangNhap_409() throws Exception {
        when(createGuard.create(any())).thenThrow(new DomainException(ErrorCode.USERNAME_EXISTS));

        mvc.perform(post(URL).with(csrf()).with(authentication(manager()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Ngô Văn H","phone":"0912345678","username":"baove03","password":"MatKhau@123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_EXISTS"));
    }

    private static UsernamePasswordAuthenticationToken manager() {
        SessionUser principal = () -> 1L;
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("GUARD_CREATE")));
    }
}