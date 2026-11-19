package com.ptit.parking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Đợt 5 — test TÍCH HỢP trên MySQL thật: migration V018; mua vé tháng (giao dịch MUA_VE, chống mua trùng, gửi lặp
 * không trừ lần hai); xe có vé ra MIỄN PHÍ, không ghi giao dịch (PassAwareFeePolicy); thông báo được ghi SAU KHI
 * nghiệp vụ commit (Observer) — mua vé, số dư thấp, khiếu nại mới; đánh dấu đã đọc; doanh thu tính cả tiền bán vé;
 * R12 bảo toàn điểm.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import(PassAndNotificationIntegrationTest.FixedClockConfig.class)
class PassAndNotificationIntegrationTest {

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

    /** 10:00 sáng 20/11/2026 giờ Việt Nam. */
    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-20T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    private static final String API = "/api/v1";
    private static final String PASSWORD = "MatKhau@123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void veThang_xeRaMienPhi_thongBao() throws Exception {
        MockHttpSession manager = login("quanly01", "Quanly@123");
        MockHttpSession owner = login("chutk01", "Chutk@123");
        MockHttpSession pass = registerStudent("PS001", "svps001", "29P12345", 120_000);

        // ===== Mua vé tháng =====
        MvcResult overview = mvc.perform(get(API + "/me/passes").session(pass))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentMonth").value("2026-11"))
                .andExpect(jsonPath("$.nextMonth").value("2026-12"))
                .andExpect(jsonPath("$.options[0].price").value(100_000))
                .andExpect(jsonPath("$.options[0].current").doesNotExist())
                .andReturn();
        long vehicleId = longAt(overview, "$.options[0].vehicleId");
        send(pass, keyed(post(API + "/me/passes"), UUID.randomUUID()), buy(vehicleId, "2027-01"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("PASS_MONTH_INVALID"));

        UUID key = UUID.randomUUID();
        send(pass, keyed(post(API + "/me/passes"), key), buy(vehicleId, "2026-11"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pass.validUntil").value("2026-11-30"))
                .andExpect(jsonPath("$.balanceAfter").value(20_000));
        send(pass, keyed(post(API + "/me/passes"), key), buy(vehicleId, "2026-11"))     // bấm lại cùng key
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.balanceAfter").value(20_000));
        send(pass, keyed(post(API + "/me/passes"), UUID.randomUUID()), buy(vehicleId, "2026-11"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PASS_ALREADY_EXISTS"));
        assertThat(balanceOf("svps001")).isEqualTo(20_000);
        assertThat(count("SELECT COUNT(*) FROM point_transactions WHERE type = 'MUA_VE'")).isEqualTo(1);

        // Biên nhận trong hộp thông báo (ghi sau khi commit)
        mvc.perform(get(API + "/me/notifications").session(pass))
                .andExpect(jsonPath("$.unread").value(1))
                .andExpect(jsonPath("$.items[0].kind").value("VE_THANG"))
                .andExpect(jsonPath("$.items[0].title").value("Đã mua vé tháng 11/2026"));

        // ===== Xe có vé: vào / ra miễn phí, không ghi giao dịch thanh toán =====
        send(manager, post(API + "/manager/guards"), """
                {"fullName":"Bảo Vệ Vé Tháng","phone":"0912345678","username":"bvps001","password":"%s"}"""
                .formatted(PASSWORD)).andExpect(status().isCreated());
        MockHttpSession guard = login("bvps001", PASSWORD);
        send(guard, post(API + "/guard/shifts/start"), "{\"openingCash\":0}").andExpect(status().isOk());
        mvc.perform(get(API + "/guard/check-in/preview").param("plate", "29P12345").session(guard))
                .andExpect(jsonPath("$.minFee").value(0))
                .andExpect(jsonPath("$.passValidUntil").value("2026-11-30"));
        send(guard, post(API + "/guard/check-in"), "{\"plateNumber\":\"29P12345\"}").andExpect(status().isCreated());
        MvcResult preview = mvc.perform(get(API + "/guard/check-out/preview").param("plate", "29P12345").session(guard))
                .andExpect(jsonPath("$.fee").value(0))
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.passValidUntil").value("2026-11-30"))
                .andReturn();
        UUID exitKey = UUID.randomUUID();
        String exitBody = "{\"sessionId\":" + longAt(preview, "$.sessionId") + "}";
        send(guard, keyed(post(API + "/guard/check-out"), exitKey), exitBody)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").doesNotExist())
                .andExpect(jsonPath("$.fee").value(0))
                .andExpect(jsonPath("$.balanceAfter").value(20_000))
                .andExpect(jsonPath("$.passValidUntil").value("2026-11-30"));
        send(guard, keyed(post(API + "/guard/check-out"), exitKey), exitBody)              // bảo vệ bấm lại
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"));
        assertThat(count("SELECT COUNT(*) FROM point_transactions WHERE type = 'THANH_TOAN'")).isZero();
        assertThat(balanceOf("svps001")).isEqualTo(20_000);

        // ===== Xe KHÔNG có vé: trả phí, số dư còn 1.000 < 3.000 -> nhắc đổi thêm điểm =====
        MockHttpSession low = registerStudent("PS002", "svps002", "29P67890", 4_000);
        send(guard, post(API + "/guard/check-in"), "{\"plateNumber\":\"29P67890\"}").andExpect(status().isCreated());
        MvcResult lowPreview = mvc.perform(get(API + "/guard/check-out/preview").param("plate", "29P67890").session(guard))
                .andExpect(jsonPath("$.fee").value(3000))
                .andExpect(jsonPath("$.passValidUntil").doesNotExist())
                .andReturn();
        send(guard, keyed(post(API + "/guard/check-out"), UUID.randomUUID()),
                "{\"sessionId\":" + longAt(lowPreview, "$.sessionId") + "}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.transactionId").isNumber());
        mvc.perform(get(API + "/me/notifications").session(low))
                .andExpect(jsonPath("$.items[0].kind").value("SO_DU_THAP"))
                .andExpect(jsonPath("$.items[0].body").value(org.hamcrest.Matchers.containsString("1.000")));

        // ===== Khiếu nại mới -> mọi quản lý nhận thông báo; đánh dấu đã đọc =====
        send(pass, post(API + "/me/complaints"), "{\"type\":\"KHAC\",\"description\":\"Góp ý mái che\"}")
                .andExpect(status().isCreated());
        MvcResult inbox = mvc.perform(get(API + "/me/notifications").session(manager))
                .andExpect(jsonPath("$.items[0].kind").value("KHIEU_NAI"))
                .andReturn();
        long managerUnread = longAt(inbox, "$.unread");
        assertThat(managerUnread).isPositive();
        long first = longAt(inbox, "$.items[0].id");
        send(manager, post(API + "/me/notifications/" + first + "/read"), "")
                .andExpect(jsonPath("$.unread").value(managerUnread - 1))
                .andExpect(jsonPath("$.items[0].read").value(true));
        send(pass, post(API + "/me/notifications/" + first + "/read"), "");               // id của người khác: bỏ qua
        send(manager, post(API + "/me/notifications/read-all"), "").andExpect(jsonPath("$.unread").value(0));

        // ===== Doanh thu chủ bãi tính cả tiền bán vé; sổ giao dịch có MUA_VE; R12 =====
        mvc.perform(get(API + "/owner/revenue").param("from", "2026-11-20").param("to", "2026-11-20").session(owner))
                .andExpect(jsonPath("$.totalRevenue").value(103_000))
                .andExpect(jsonPath("$.totalPayments").value(1));
        mvc.perform(get(API + "/manager/ledger").param("type", "MUA_VE").session(manager))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].from.studentCode").value("PS001"))
                .andExpect(jsonPath("$.items[0].to.role").value("CHU_TAI_KHOAN"));
        assertThat(jdbc.sql("SELECT points_balanced FROM v_reconciliation").query(Long.class).single()).isEqualTo(1L);

        // ===== Phân quyền =====
        mvc.perform(get(API + "/me/passes").session(guard)).andExpect(status().isForbidden());
        mvc.perform(get(API + "/me/notifications").session(guard)).andExpect(status().isOk());  // mọi vai trò có hộp thư
    }

    private MockHttpSession registerStudent(String code, String username, String plate, long cash) throws Exception {
        MockHttpSession manager = login("quanly01", "Quanly@123");
        send(null, post(API + "/students/register"), """
                {"studentCode":"%s","fullName":"Sinh Viên %s","username":"%s","password":"%s"}"""
                .formatted(code, code, username, PASSWORD)).andExpect(status().isCreated());
        MockHttpSession student = login(username, PASSWORD);
        send(student, post(API + "/me/vehicles"), "{\"plateNumber\":\"" + plate + "\",\"vehicleTypeCode\":\"XE_MAY\"}")
                .andExpect(status().isCreated());
        send(manager, keyed(post(API + "/manager/deposits"), UUID.randomUUID()),
                "{\"studentCode\":\"" + code + "\",\"cashAmount\":" + cash + "}").andExpect(status().isCreated());
        return student;
    }

    private static String buy(long vehicleId, String month) {
        return "{\"vehicleId\":" + vehicleId + ",\"month\":\"" + month + "\"}";
    }

    private long balanceOf(String username) {
        return jdbc.sql("SELECT w.balance FROM wallets w JOIN users u ON u.id = w.user_id WHERE u.username = :u")
                .param("u", username).query(Long.class).single();
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    private static MockHttpServletRequestBuilder keyed(MockHttpServletRequestBuilder request, UUID key) {
        return request.header("Idempotency-Key", key.toString());
    }

    private ResultActions send(MockHttpSession session, MockHttpServletRequestBuilder request, String json)
            throws Exception {
        if (!json.isEmpty()) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        if (session != null) {
            request.session(session).with(csrf());
        }
        return mvc.perform(request);
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = send(null, post(API + "/auth/login"),
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static long longAt(MvcResult result, String path) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return value.longValue();
    }
}
