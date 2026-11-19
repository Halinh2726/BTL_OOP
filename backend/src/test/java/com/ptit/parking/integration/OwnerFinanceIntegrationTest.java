package com.ptit.parking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Đợt 3 — test TÍCH HỢP trên MySQL thật: migration V015, yêu cầu rút điểm (R15–R18), giao dịch điều chỉnh,
 * tổng quan và doanh thu theo ngày (giờ Việt Nam) của chủ bãi xe, R12 bảo toàn điểm sau tất cả các bước.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import(OwnerFinanceIntegrationTest.FixedClockConfig.class)
class OwnerFinanceIntegrationTest {

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

    /** 10:00 sáng 20/11 giờ Việt Nam: phí 3.000 điểm. */
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
    void rutDiemTheoYeuCau_dieuChinh_tongQuan_doanhThu() throws Exception {
        MockHttpSession manager = login("quanly01", "Quanly@123");
        MockHttpSession owner = login("chutk01", "Chutk@123");

        // Chuẩn bị: sinh viên đổi 20.000 đ (quỹ tiền mặt 20.000), gửi xe 2 lượt -> chủ bãi có 6.000 điểm
        send(null, post(API + "/students/register"), """
                {"studentCode":"OF001","fullName":"Sinh Viên Tài Chính","username":"svof001","password":"%s"}"""
                .formatted(PASSWORD)).andExpect(status().isCreated());
        MockHttpSession student = login("svof001", PASSWORD);
        send(student, post(API + "/me/vehicles"), "{\"plateNumber\":\"29F12345\",\"vehicleTypeCode\":\"XE_MAY\"}")
                .andExpect(status().isCreated());
        send(manager, keyed(post(API + "/manager/deposits")), "{\"studentCode\":\"OF001\",\"cashAmount\":20000}")
                .andExpect(status().isCreated());
        send(manager, post(API + "/manager/guards"), """
                {"fullName":"Bảo Vệ Tài Chính","phone":"0912345678","username":"bvof001","password":"%s"}"""
                .formatted(PASSWORD)).andExpect(status().isCreated());
        MockHttpSession guard = login("bvof001", PASSWORD);
        long firstPayment = parkOnce(guard);
        parkOnce(guard);

        mvc.perform(get(API + "/owner/overview").session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(6000))
                .andExpect(jsonPath("$.revenueToday").value(6000))
                .andExpect(jsonPath("$.paymentsToday").value(2))
                .andExpect(jsonPath("$.cashHeld").value(20000));

        // Giao dịch điều chỉnh: lượt đầu bị coi là trừ nhầm -> hoàn 3.000 điểm cho sinh viên, bắt buộc có lý do
        send(manager, keyed(post(API + "/manager/adjustments")), """
                {"studentCode":"of001","direction":"HOAN_DIEM","points":3000,"reason":""}""")
                .andExpect(status().isBadRequest());
        send(manager, keyed(post(API + "/manager/adjustments")), """
                {"studentCode":"of001","direction":"HOAN_DIEM","points":3000,"reason":"Trừ phí nhầm","originalTransactionId":%d}"""
                .formatted(firstPayment))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentName").value("Sinh Viên Tài Chính"));
        mvc.perform(get(API + "/me/transactions").session(student))
                .andExpect(jsonPath("$.items[0].type").value("DIEU_CHINH"))
                .andExpect(jsonPath("$.items[0].change").value(3000));

        // R15: chủ bãi còn 3.000 điểm
        createRequest(owner, 5000).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_EXCEEDS_AVAILABLE"));
        long rejected = longAt(createRequest(owner, 2000).andExpect(status().isCreated()).andReturn(), "$.id");
        createRequest(owner, 2000).andExpect(jsonPath("$.code").value("WITHDRAWAL_EXCEEDS_AVAILABLE"));
        send(manager, post(API + "/manager/withdrawal-requests/" + rejected + "/reject"), "{\"note\":\"Tạo lại số tròn\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TU_CHOI"))
                .andExpect(jsonPath("$.rejectReason").value("Tạo lại số tròn"));

        // Trả tiền -> trừ điểm; chủ bãi khiếu nại -> quản lý kiểm tra xong
        long paid = longAt(createRequest(owner, 3000).andExpect(status().isCreated()).andReturn(), "$.id");
        mvc.perform(get(API + "/manager/withdrawal-requests").param("status", "CHO_XU_LY").session(manager))
                .andExpect(jsonPath("$.cashHeld").value(20000))
                .andExpect(jsonPath("$.items[0].id").value(paid));
        send(manager, post(API + "/manager/withdrawal-requests/" + paid + "/pay"), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request.status").value("DA_TRA"))
                .andExpect(jsonPath("$.request.processedByName").value("Phạm Văn Quản"));
        send(owner, post(API + "/owner/withdrawal-requests/" + paid + "/complain"), "{\"note\":\"Mới nhận 2.000 đ\"}")
                .andExpect(jsonPath("$.status").value("KHIEU_NAI"));
        send(manager, post(API + "/manager/withdrawal-requests/" + paid + "/resolve"), "{\"note\":\"Đã đếm lại, trả đủ\"}")
                .andExpect(jsonPath("$.status").value("DA_XAC_NHAN"));

        mvc.perform(get(API + "/owner/overview").session(owner))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.cashHeld").value(17000));
        mvc.perform(get(API + "/owner/revenue").param("from", "2026-11-20").param("to", "2026-11-20").session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].date").value("2026-11-20"))
                .andExpect(jsonPath("$.totalRevenue").value(6000))
                .andExpect(jsonPath("$.totalAdjustments").value(-3000))
                .andExpect(jsonPath("$.totalCashIn").value(20000))
                .andExpect(jsonPath("$.totalCashOut").value(3000));

        // Quyền: chủ bãi không xem / xử lý được yêu cầu bên quản lý; quản lý không tạo được yêu cầu rút
        mvc.perform(get(API + "/manager/withdrawal-requests").session(owner)).andExpect(status().isForbidden());
        createRequest(manager, 1000).andExpect(status().isForbidden());

        // R12: tổng điểm trong mọi ví = điểm đã đổi − điểm đã rút (điều chỉnh chỉ là chuyển điểm)
        long inWallets = jdbc.sql("SELECT COALESCE(SUM(balance), 0) FROM wallets").query(Long.class).single();
        Long balanced = jdbc.sql("SELECT points_balanced FROM v_reconciliation").query(Long.class).single();
        assertThat(balanced).isEqualTo(1L);
        assertThat(inWallets).isEqualTo(20000 - 3000);
    }

    private long parkOnce(MockHttpSession guard) throws Exception {
        send(guard, post(API + "/guard/check-in"), "{\"plateNumber\":\"29F12345\"}").andExpect(status().isCreated());
        MvcResult preview = mvc.perform(get(API + "/guard/check-out/preview").param("plate", "29F12345").session(guard))
                .andReturn();
        long sessionId = longAt(preview, "$.sessionId");
        MvcResult paid = send(guard, keyed(post(API + "/guard/check-out")), "{\"sessionId\":" + sessionId + "}")
                .andExpect(status().isCreated()).andReturn();
        return longAt(paid, "$.transactionId");
    }

    private ResultActions createRequest(MockHttpSession session, long points) throws Exception {
        return send(session, post(API + "/owner/withdrawal-requests"), "{\"points\":" + points + "}");
    }

    private static MockHttpServletRequestBuilder keyed(MockHttpServletRequestBuilder request) {
        return request.header("Idempotency-Key", UUID.randomUUID().toString());
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
