package com.ptit.parking.shared.security;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ptit.parking.shared.security.AccountStatusLookup.AccountState;

/**
 * Chính sách phiên (Đặc tả API mục 3): tài khoản bị khóa giữa chừng bị đá ra ngay,
 * phiên quá 12 giờ phải đăng nhập lại.
 */
@WebMvcTest(controllers = SessionPolicyTest.MeApi.class)
@Import({SessionPolicyTest.MeApi.class, WebSecurityTestConfig.class})
class SessionPolicyTest {

    private static final SessionUser GUARD = () -> 4L;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AccountStatusLookup accounts;

    @Test
    void taiKhoanDangHoatDong_goiApiBinhThuong() throws Exception {
        when(accounts.stateOf(4L)).thenReturn(AccountState.ACTIVE);

        mvc.perform(get("/api/v1/me").with(authentication(loggedIn())))
                .andExpect(status().isOk());
    }

    @Test
    void quanLyVuaKhoaBaoVe_requestKeTiepBi403AccountLocked_vaPhienBiHuy() throws Exception {
        when(accounts.stateOf(4L)).thenReturn(AccountState.LOCKED);
        MockHttpSession session = new MockHttpSession();

        mvc.perform(get("/api/v1/me").session(session).with(authentication(loggedIn())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));

        assertThat(session.isInvalid()).isTrue();          // R14 + QL04: phiên của bảo vệ bị hủy ngay
    }

    @Test
    void taiKhoanKhongConTonTai_bi401() throws Exception {
        when(accounts.stateOf(4L)).thenReturn(AccountState.NOT_FOUND);

        mvc.perform(get("/api/v1/me").with(authentication(loggedIn())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void phienQua12Gio_bi401_vaPhienBiHuy() throws Exception {
        when(accounts.stateOf(4L)).thenReturn(AccountState.ACTIVE);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionLifetimeFilter.SIGNED_IN_AT, Instant.now().minus(Duration.ofHours(13)));

        mvc.perform(get("/api/v1/me").session(session).with(authentication(loggedIn())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void phienMoi11Gio_vanHopLe() throws Exception {
        when(accounts.stateOf(4L)).thenReturn(AccountState.ACTIVE);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionLifetimeFilter.SIGNED_IN_AT, Instant.now().minus(Duration.ofHours(11)));

        mvc.perform(get("/api/v1/me").session(session).with(authentication(loggedIn())))
                .andExpect(status().isOk());
    }

    private static UsernamePasswordAuthenticationToken loggedIn() {
        return UsernamePasswordAuthenticationToken.authenticated(GUARD, null, List.of());
    }

    @RestController
    static class MeApi {

        @GetMapping("/api/v1/me")
        void me() {
        }
    }
}
