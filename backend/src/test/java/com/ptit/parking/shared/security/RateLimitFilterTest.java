package com.ptit.parking.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Giới hạn tần suất (Đặc tả API mục 11) qua bộ lọc thật: đăng ký 10 lần/giờ/IP, 120 request/phút/phiên.
 * Bộ lọc giữ bộ đếm suốt các test trong lớp này, nên mỗi test dùng IP / phiên riêng.
 */
@WebMvcTest(controllers = RateLimitFilterTest.Api.class)
@Import({RateLimitFilterTest.Api.class, WebSecurityTestConfig.class})
class RateLimitFilterTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void dangKy_10LanMoiGioMoiIp_lanThu11Bi429KemRetryAfter_ipKhacVanDuoc() throws Exception {
        for (int i = 0; i < RateLimitFilter.REGISTER_LIMIT; i++) {
            mvc.perform(register("10.0.0.1")).andExpect(status().isOk());
        }

        mvc.perform(register("10.0.0.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                .andExpect(retryAfterBetween(3_500, 3_600));               // chờ gần 1 giờ
        mvc.perform(register("10.0.0.2")).andExpect(status().isOk());
    }

    @Test
    void moiPhien120RequestMoiPhut_requestThu121Bi429_phienKhacVanDuoc() throws Exception {
        MockHttpSession session = new MockHttpSession();
        for (int i = 0; i < RateLimitFilter.SESSION_LIMIT; i++) {
            mvc.perform(ping(session)).andExpect(status().isOk());
        }

        mvc.perform(ping(session))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                .andExpect(retryAfterBetween(1, 60));
        mvc.perform(ping(new MockHttpSession())).andExpect(status().isOk());
    }

    @Test
    void dangKyKhongTinhVaoGioiHanCuaPhien_vaGioiHanPhienKhongApChoDangKy() throws Exception {
        MockHttpSession session = new MockHttpSession();
        for (int i = 0; i < RateLimitFilter.REGISTER_LIMIT; i++) {
            mvc.perform(register("10.0.0.3").session(session)).andExpect(status().isOk());
        }

        mvc.perform(ping(session)).andExpect(status().isOk());           // 10 lần đăng ký không ăn vào 120 của phiên
    }

    private static MockHttpServletRequestBuilder register(String ip) {
        return post("/api/v1/students/register").with(request -> {
            request.setRemoteAddr(ip);
            return request;
        });
    }

    private static MockHttpServletRequestBuilder ping(MockHttpSession session) {
        SessionUser user = () -> 3L;
        return get("/api/v1/ping").session(session)
                .with(authentication(UsernamePasswordAuthenticationToken.authenticated(user, null, List.of())));
    }

    private static ResultMatcher retryAfterBetween(long min, long max) {
        return result -> assertThat(Long.parseLong(result.getResponse().getHeader("Retry-After"))).isBetween(min, max);
    }

    @RestController
    static class Api {

        @PostMapping("/api/v1/students/register")
        void register() {
        }

        @GetMapping("/api/v1/ping")
        void ping() {
        }
    }
}