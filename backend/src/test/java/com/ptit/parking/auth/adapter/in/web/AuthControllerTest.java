package com.ptit.parking.auth.adapter.in.web;

import java.util.List;

import static org.hamcrest.Matchers.notNullValue;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
import com.ptit.parking.shared.security.WebSecurityTestConfig;

/**
 * Test tầng web + bảo mật của C01. Use case được thay bằng mock: ở đây chỉ kiểm tra
 * HTTP, phiên, CSRF và định dạng lỗi — logic đăng nhập đã có LoginUseCaseTest.
 */
@WebMvcTest(AuthController.class)
@Import({WebSecurityTestConfig.class, SessionSignIn.class, LoginThrottle.class})
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

        // Bộ đếm đăng nhập sai sống suốt các test của lớp này: mỗi test dùng tên đăng nhập riêng.

    @Test
    void sai5LanTrong1Phut_lanThu6Bi429KemRetryAfter_khongKiemTraMatKhau() throws Exception {
        when(loginUseCase.login(anyString(), eq("sai"))).thenThrow(new DomainException(ErrorCode.INVALID_CREDENTIALS));

        for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) {
            mvc.perform(login(i % 2 == 0 ? "baove09" : "BAOVE09", "sai"))        // hoa / thường vẫn là một tài khoản
                    .andExpect(status().isUnauthorized());
        }

        mvc.perform(login("baove09", "sai"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                .andExpect(header().exists("Retry-After"));
        verify(loginUseCase, times(LoginThrottle.MAX_FAILURES)).login(anyString(), eq("sai"));
        mvc.perform(login("baove10", "sai")).andExpect(status().isUnauthorized());   // tài khoản khác không bị ảnh hưởng
    }

    @Test
    void dangNhapDung_xoaCacLanSaiTruocDo() throws Exception {
        when(loginUseCase.login("sv77", "sai")).thenThrow(new DomainException(ErrorCode.INVALID_CREDENTIALS));
        when(loginUseCase.login("sv77", "Dung@123")).thenReturn(MANAGER);

        for (int i = 0; i < LoginThrottle.MAX_FAILURES - 1; i++) {
            mvc.perform(login("sv77", "sai")).andExpect(status().isUnauthorized());
        }
        mvc.perform(login("sv77", "Dung@123")).andExpect(status().isOk());

        for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) {
            mvc.perform(login("sv77", "sai")).andExpect(status().isUnauthorized());      // không phải 429
        }
    }

    private static MockHttpServletRequestBuilder login(String username, String password) {
        return post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
    }

    private static UsernamePasswordAuthenticationToken loggedIn(AuthenticatedUser user) {
        return UsernamePasswordAuthenticationToken.authenticated(user, null, List.of());
    }
}