package com.ptit.parking.auth.adapter.in.web;

import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ptit.parking.auth.application.AuthenticatedUser;
import com.ptit.parking.auth.application.usecase.GetCurrentUserUseCase;
import com.ptit.parking.auth.application.usecase.LoginUseCase;
import com.ptit.parking.auth.domain.Role;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.shared.security.JsonSecurityErrorHandler;
import com.ptit.parking.shared.security.SecurityConfig;
import com.ptit.parking.shared.web.ConstraintErrorTranslator;
import com.ptit.parking.shared.web.GlobalExceptionHandler;
import com.ptit.parking.shared.web.TraceIdFilter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Test tầng web + bảo mật của C01. Use case được thay bằng mock: ở đây chỉ kiểm tra
 * HTTP, phiên, CSRF và định dạng lỗi — logic đăng nhập đã có LoginUseCaseTest.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JsonSecurityErrorHandler.class, SessionSignIn.class,
        GlobalExceptionHandler.class, ConstraintErrorTranslator.class, TraceIdFilter.class})
class AuthControllerTest {

    private static final AuthenticatedUser MANAGER = new AuthenticatedUser(
            1L, "quanly01", "Phạm Văn Quản", Role.QUAN_LY, List.of("CASH_DEPOSIT", "GUARD_LOCK"));

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private LoginUseCase loginUseCase;

    @MockitoBean
    private GetCurrentUserUseCase getCurrentUser;

    @Test
    void dangNhapDung_tra200_vaLuuNguoiDungVaoPhien() throws Exception {
        when(loginUseCase.login("quanly01", "Quanly@123")).thenReturn(MANAGER);

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)       // không cần CSRF
                        .content("{\"username\":\"quanly01\",\"password\":\"Quanly@123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(1))
                .andExpect(jsonPath("$.user.role").value("QUAN_LY"))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.permissions[0]").value("CASH_DEPOSIT"))
                .andExpect(request().sessionAttribute("SPRING_SECURITY_CONTEXT", notNullValue()));
    }

    @Test
    void dangNhapSai_tra401InvalidCredentials() throws Exception {
        when(loginUseCase.login("quanly01", "sai")).thenThrow(new DomainException(ErrorCode.INVALID_CREDENTIALS));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"quanly01\",\"password\":\"sai\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Sai tên đăng nhập hoặc mật khẩu"));
    }

    @Test
    void dangNhapThieuTruong_tra400ValidationError() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0].field").value("username"))
                .andExpect(jsonPath("$.fields[0].code").value("REQUIRED"));
    }

    @Test
    void me_chuaDangNhap_tra401Unauthenticated() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void me_daDangNhap_traThongTinPhien() throws Exception {
        when(getCurrentUser.get(1L)).thenReturn(MANAGER);

        mvc.perform(get("/api/v1/me").with(authentication(loggedIn(MANAGER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("quanly01"))
                .andExpect(jsonPath("$.permissions.length()").value(2));
    }

    @Test
    void me_taiKhoanVuaBiKhoa_tra403AccountLocked() throws Exception {
        when(getCurrentUser.get(1L)).thenThrow(new DomainException(ErrorCode.ACCOUNT_LOCKED));

        mvc.perform(get("/api/v1/me").with(authentication(loggedIn(MANAGER))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void dangXuat_coCsrf_tra204() throws Exception {
        mvc.perform(post("/api/v1/auth/logout").with(authentication(loggedIn(MANAGER))).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void dangXuat_thieuCsrf_khiDaDangNhap_tra403CsrfInvalid() throws Exception {
        mvc.perform(post("/api/v1/auth/logout").with(authentication(loggedIn(MANAGER))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void goiApiGhiDuLieu_khiChuaDangNhap_tra401ChuKhongPhai403() throws Exception {
        mvc.perform(post("/api/v1/manager/deposits").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private static UsernamePasswordAuthenticationToken loggedIn(AuthenticatedUser user) {
        return UsernamePasswordAuthenticationToken.authenticated(user, null, List.of());
    }
}