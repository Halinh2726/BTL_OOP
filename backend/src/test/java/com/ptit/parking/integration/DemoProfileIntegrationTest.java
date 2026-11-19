package com.ptit.parking.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ptit.parking.account.application.port.out.SecretGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Profile demo trên MySQL thật: mọi tài khoản có sẵn đăng nhập bằng demo1234; bảo vệ tự cập nhật thông tin cá nhân
 * (số điện thoại bắt buộc) và tự khôi phục mật khẩu bằng mã gửi qua email.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Testcontainers(disabledWithoutDocker = true)
@Import(DemoProfileIntegrationTest.FixedOtpConfig.class)
class DemoProfileIntegrationTest {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci",
                    "--default-time-zone=+00:00", "--log-bin-trust-function-creators=1");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> MYSQL.getJdbcUrl()
                + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @TestConfiguration
    static class FixedOtpConfig {

        @Bean
        @Primary
        SecretGenerator fixedSecrets() {
            return new SecretGenerator() {
                @Override
                public String oneTimeCode() {
                    return "246810";
                }

                @Override
                public String temporaryPassword() {
                    return "TamThoi2345";
                }
            };
        }
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void taiKhoanCoSan_dangNhapBangDemo1234_matKhauCuKhongConDung() throws Exception {
        login("quanly01", "demo1234").andExpect(status().isOk());
        login("chutk01", "demo1234").andExpect(status().isOk());
        login("quanly01", "Quanly@123").andExpect(status().isUnauthorized());
    }

    @Test
    void baoVe_capNhatThongTinCaNhan_quenMatKhauBangEmail() throws Exception {
        MockHttpSession manager = session(login("quanly01", "demo1234"));
        call(manager, post("/api/v1/manager/guards"), """
                {"fullName":"Bảo Vệ Demo","phone":"0912345678","username":"bvdemo01","password":"BaoVe@789"}""")
                .andExpect(status().isCreated());
        MockHttpSession guard = session(login("bvdemo01", "BaoVe@789"));

        // Bảo vệ tự sửa họ tên, email, số điện thoại; xóa số điện thoại thì bị từ chối (bắt buộc với bảo vệ)
        call(guard, patch("/api/v1/me/profile"), """
                {"fullName":"Bảo Vệ Đã Sửa","email":"BVDEMO01@ptit.edu.vn","phone":"0987 654 321"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("bvdemo01@ptit.edu.vn"))
                .andExpect(jsonPath("$.phone").value("0987654321"))
                .andExpect(jsonPath("$.phoneRequired").value(true));
        call(guard, patch("/api/v1/me/profile"), """
                {"fullName":"Bảo Vệ Đã Sửa","email":"bvdemo01@ptit.edu.vn","phone":""}""")
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/manager/guards").session(manager))
                .andExpect(jsonPath("$[?(@.username == 'bvdemo01')].phone").value("0987654321"));

        // Quên mật khẩu: mã gửi về email vừa khai báo
        call(null, post("/api/v1/auth/password-reset"), "{\"email\":\"bvdemo01@ptit.edu.vn\"}")
                .andExpect(status().isAccepted());
        call(null, post("/api/v1/auth/password-reset/confirm"), """
                {"email":"bvdemo01@ptit.edu.vn","code":"246810","newPassword":"BaoVeMoi@1"}""")
                .andExpect(status().isNoContent());
        login("bvdemo01", "BaoVeMoi@1").andExpect(status().isOk())
                .andExpect(jsonPath("$.user.fullName").value("Bảo Vệ Đã Sửa"));
    }

    private ResultActions call(MockHttpSession session,
                               org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               String json) throws Exception {
        request.contentType(MediaType.APPLICATION_JSON).content(json);
        if (session != null) {
            request.session(session).with(csrf());
        }
        return mvc.perform(request);
    }

    private ResultActions login(String username, String password) throws Exception {
        return call(null, post("/api/v1/auth/login"),
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
    }

    private static MockHttpSession session(ResultActions loggedIn) throws Exception {
        MvcResult result = loggedIn.andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
