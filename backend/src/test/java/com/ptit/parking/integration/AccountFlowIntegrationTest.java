package com.ptit.parking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ptit.parking.account.application.port.out.SecretGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Đợt 1 — test TÍCH HỢP trên MySQL 8.4 thật: migration V011, các câu SQL mới và bộ lọc phiên chạy cùng nhau.
 * Đồng hồ giả TUA ĐƯỢC (đổi mật khẩu phải xảy ra SAU lúc đăng nhập thì mới thấy phiên cũ bị đăng xuất);
 * mã OTP và mật khẩu tạm cố định để test biết trước. Máy không có Docker thì cả lớp được bỏ qua.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import(AccountFlowIntegrationTest.TestConfig.class)
class AccountFlowIntegrationTest {

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

    static final String OTP = "654321";
    static final String TEMPORARY = "TamThoi2345";

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        TickingClock tickingClock() {
            return new TickingClock(Instant.parse("2026-11-02T03:00:00Z"));
        }

        @Bean
        @Primary
        SecretGenerator fixedSecrets() {
            return new SecretGenerator() {
                @Override
                public String oneTimeCode() {
                    return OTP;
                }

                @Override
                public String temporaryPassword() {
                    return TEMPORARY;
                }
            };
        }
    }

    private static final String API = "/api/v1";
    private static final String PASSWORD = "MatKhau@123";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private TickingClock clock;

    @Test
    void capNhatThongTin_quenMatKhauBangOtp_phienCuBiDangXuat() throws Exception {
        String username = registerStudent();
        MockHttpSession oldSession = login(username, PASSWORD);
        String email = username + "@ptit.edu.vn";

        mvc.perform(patch(API + "/me/profile").session(oldSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("fullName", "Sinh Viên Đợt Một", "email", email.toUpperCase(), "phone", "091.234.5678")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.phone").value("0912345678"));

        // Email đã thuộc tài khoản khác -> 409
        MockHttpSession other = login(registerStudent(), PASSWORD);
        mvc.perform(patch(API + "/me/profile").session(other).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("fullName", "Người Khác", "email", email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_EXISTS"));

        clock.advanceSeconds(30);
        mvc.perform(post(API + "/auth/password-reset").contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", email)))
                .andExpect(status().isAccepted());
        confirmReset(email, "000000", "MatKhauMoi@1")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RESET_CODE_INVALID"));
        assertThat(jdbc.sql("SELECT MAX(failed_attempts) FROM password_reset_codes").query(Integer.class).single())
                .isEqualTo(1);                                       // lần sai đã được LƯU dù request báo lỗi
        confirmReset(email, OTP, "MatKhauMoi@1").andExpect(status().isNoContent());

        mvc.perform(get(API + "/me").session(oldSession)).andExpect(status().isUnauthorized());
        login(username, "MatKhauMoi@1");
    }

    @Test
    void doiMatKhau_phienHienTaiDungTiep_phienKhacBiDangXuat() throws Exception {
        String username = registerStudent();
        MockHttpSession laptop = login(username, PASSWORD);
        MockHttpSession phone = login(username, PASSWORD);

        clock.advanceSeconds(30);
        mvc.perform(post(API + "/me/password").session(laptop).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("currentPassword", PASSWORD, "newPassword", "MatKhauMoi@2")))
                .andExpect(status().isNoContent());

        mvc.perform(get(API + "/me").session(laptop)).andExpect(status().isOk());
        mvc.perform(get(API + "/me").session(phone)).andExpect(status().isUnauthorized());
    }

    @Test
    void quanLyCapMatKhauTam_sinhVienBatBuocDoiTruoc_ghiNhatKy() throws Exception {
        String username = registerStudent();
        String code = codeOf(username);
        MockHttpSession manager = login("quanly01", "Quanly@123");

        clock.advanceSeconds(30);
        mvc.perform(post(API + "/manager/students/" + code.toLowerCase() + "/password-reset").session(manager).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.temporaryPassword").value(TEMPORARY));

        clock.advanceSeconds(30);
        MvcResult loggedIn = loginExpect(username, TEMPORARY)
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andReturn();
        MockHttpSession student = (MockHttpSession) loggedIn.getRequest().getSession(false);
        mvc.perform(get(API + "/me/vehicles").session(student))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mvc.perform(post(API + "/me/password").session(student).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("currentPassword", TEMPORARY, "newPassword", "MatKhauRieng@1")))
                .andExpect(status().isNoContent());
        mvc.perform(get(API + "/me/vehicles").session(student)).andExpect(status().isOk());

        mvc.perform(get(API + "/manager/audit-logs").session(manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.action == 'CAP_MAT_KHAU_TAM')].actorName").value("Phạm Văn Quản"));
    }

    @Test
    void xe_maXeDap_toiDa3Xe_ngungSuDung_bienSoDuocGiaiPhong_baoVeKhongThayXeDaNgung() throws Exception {
        MockHttpSession first = login(registerStudent(), PASSWORD);

        MvcResult bike = registerVehicle(first, "", "XE_DAP")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plateDisplay").value(org.hamcrest.Matchers.startsWith("XD-")))
                .andReturn();
        String plate = "30K" + (10000 + SEQ.incrementAndGet());
        long carId = longAt(registerVehicle(first, plate, "XE_MAY").andExpect(status().isCreated()).andReturn(), "$.id");
        registerVehicle(first, "30K" + (20000 + SEQ.incrementAndGet()), "XE_MAY").andExpect(status().isCreated());
        registerVehicle(first, "30K" + (30000 + SEQ.incrementAndGet()), "XE_MAY")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VEHICLE_LIMIT_REACHED"));
        assertThat(JsonPath.<String>read(bike.getResponse().getContentAsString(), "$.plateNumber")).matches("XD[0-9]{5}");

        mvc.perform(post(API + "/me/vehicles/" + carId + "/deactivate").session(first).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NGUNG_SU_DUNG"));

        MockHttpSession guard = login(createGuard(), PASSWORD);
        mvc.perform(get(API + "/guard/check-in/preview").param("plate", plate).session(guard))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.reason").value("PLATE_NOT_FOUND"));

        MockHttpSession second = login(registerStudent(), PASSWORD);
        registerVehicle(second, plate, "XE_MAY").andExpect(status().isCreated());   // người mua lại xe đăng ký được
    }

    // ===== Tiện ích =====

    private String registerStudent() throws Exception {
        int n = 5000 + SEQ.incrementAndGet();
        String username = "svdot" + n;
        mvc.perform(post(API + "/students/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("studentCode", "D1" + n, "fullName", "Sinh Viên Đợt Một",
                                "username", username, "password", PASSWORD)))
                .andExpect(status().isCreated());
        return username;
    }

    private String codeOf(String username) {
        return jdbc.sql("SELECT s.student_code FROM students s JOIN users u ON u.id = s.user_id WHERE u.username = :u")
                .param("u", username).query(String.class).single();
    }

    private String createGuard() throws Exception {
        MockHttpSession manager = login("quanly01", "Quanly@123");
        String username = "bvdot" + (5000 + SEQ.incrementAndGet());
        mvc.perform(post(API + "/manager/guards").session(manager).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("fullName", "Bảo Vệ Đợt Một", "phone", "0912345678",
                                "username", username, "password", PASSWORD)))
                .andExpect(status().isCreated());
        return username;
    }

    private ResultActions registerVehicle(MockHttpSession student, String plate, String type) throws Exception {
        return mvc.perform(post(API + "/me/vehicles").session(student).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json("plateNumber", plate, "vehicleTypeCode", type)));
    }

    private ResultActions confirmReset(String email, String code, String newPassword) throws Exception {
        return mvc.perform(post(API + "/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(json("email", email, "code", code, "newPassword", newPassword)));
    }

    private ResultActions loginExpect(String username, String password) throws Exception {
        return mvc.perform(post(API + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", username, "password", password)))
                .andExpect(status().isOk());
    }

    private MockHttpSession login(String username, String password) throws Exception {
        return (MockHttpSession) loginExpect(username, password).andReturn().getRequest().getSession(false);
    }

    private static long longAt(MvcResult result, String path) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return value.longValue();
    }

    private static String json(String... pairs) {
        StringBuilder out = new StringBuilder("{");
        for (int i = 0; i < pairs.length; i += 2) {
            out.append(i == 0 ? "" : ",").append('"').append(pairs[i]).append("\":\"").append(pairs[i + 1]).append('"');
        }
        return out.append('}').toString();
    }

    /** Đồng hồ giả tua được, giờ Việt Nam. */
    static final class TickingClock extends Clock {
        private final AtomicReference<Instant> now;

        TickingClock(Instant start) {
            this.now = new AtomicReference<>(start);
        }

        void advanceSeconds(long seconds) {
            now.updateAndGet(t -> t.plusSeconds(seconds));
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Ho_Chi_Minh");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
