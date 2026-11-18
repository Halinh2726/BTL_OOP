package com.ptit.parking.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ptit.parking.shared.security.CredentialStateLookup.CredentialState;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Đợt 1 — đổi mật khẩu đăng xuất các phiên cũ; mật khẩu tạm chặn mọi API trừ đổi mật khẩu. */
@WebMvcTest(controllers = CredentialFreshnessFilterTest.AnyApi.class)
@Import({CredentialFreshnessFilterTest.AnyApi.class, WebSecurityTestConfig.class})
class CredentialFreshnessFilterTest {

    private static final Instant LOGIN = Instant.parse("2026-11-01T01:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CredentialStateLookup credentials;

    @Test
    void doiMatKhauSauKhiPhienDangNhap_phienCuBi401_vaBiHuy() throws Exception {
        when(credentials.stateOf(7L)).thenReturn(new CredentialState(LOGIN.plusSeconds(60), false));
        MockHttpSession session = sessionVerifiedAt(LOGIN);

        mvc.perform(get("/api/v1/vi-du").session(session).with(authentication(user())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void phienVuaDoiMatKhau_dungTiepBinhThuong() throws Exception {
        when(credentials.stateOf(7L)).thenReturn(new CredentialState(LOGIN, false));

        mvc.perform(get("/api/v1/vi-du").session(sessionVerifiedAt(LOGIN)).with(authentication(user())))
                .andExpect(status().isOk());
    }

    @Test
    void matKhauTam_chanApiKhac_choPhepDoiMatKhau() throws Exception {
        when(credentials.stateOf(7L)).thenReturn(new CredentialState(LOGIN, true));
        MockHttpSession session = sessionVerifiedAt(LOGIN);

        mvc.perform(get("/api/v1/vi-du").session(session).with(authentication(user())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mvc.perform(post("/api/v1/me/password").session(session).with(csrf()).with(authentication(user())))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/me").session(session).with(authentication(user())))
                .andExpect(status().isOk());
    }

    private static MockHttpSession sessionVerifiedAt(Instant at) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CredentialFreshnessFilter.CREDENTIALS_VERIFIED_AT, at);
        return session;
    }

    private static UsernamePasswordAuthenticationToken user() {
        SessionUser principal = () -> 7L;
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
    }

    @RestController
    static class AnyApi {

        @RequestMapping("/api/v1/**")
        void any() {
        }
    }
}
