package com.ptit.parking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Đợt 2 — test TÍCH HỢP trên MySQL thật: migration V012, nhận ca → cho xe vào → xem bãi / lịch sử ca → báo sự cố có ảnh
 * → kết thúc ca (biên bản tự tổng kết) → bảo vệ ca sau phải xác nhận biên bản mới nhận ca được.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import(GateFlowIntegrationTest.FixedClockConfig.class)
class GateFlowIntegrationTest {

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
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-20T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    private static final String API = "/api/v1";
    private static final String PASSWORD = "MatKhau@123";
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void nhanCa_xeVao_xemBai_baoSuCo_banGiaoCa_caSauXacNhan() throws Exception {
        MockHttpSession manager = login("quanly01", "Quanly@123");
        MockHttpSession guardA = login(createGuard(manager, "bvgatea"), PASSWORD);
        MockHttpSession guardB = login(createGuard(manager, "bvgateb"), PASSWORD);

        // Sinh viên có xe và đủ điểm
        post(null, "/students/register", """
                {"studentCode":"G2001","fullName":"Sinh Viên Cổng","username":"svgate01","password":"%s"}""".formatted(PASSWORD))
                .andExpect(status().isCreated());
        MockHttpSession student = login("svgate01", PASSWORD);
        post(student, "/me/vehicles", "{\"plateNumber\":\"29G-123.45\",\"vehicleTypeCode\":\"XE_MAY\"}")
                .andExpect(status().isCreated());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(API + "/manager/deposits")
                        .session(manager).with(csrf()).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"studentCode\":\"G2001\",\"cashAmount\":10000}"))
                .andExpect(status().isCreated());

        // Bảo vệ A nhận ca (chưa có biên bản nào chờ), cho xe vào
        post(guardA, "/guard/shifts/start", "{\"openingCash\":200000}").andExpect(status().isOk());
        post(guardA, "/guard/check-in", "{\"plateNumber\":\"29G12345\"}").andExpect(status().isCreated());

        mvc.perform(get(API + "/guard/parked-vehicles").param("q", "123.45").session(guardA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].plateDisplay").value("29G-123.45"))
                .andExpect(jsonPath("$.items[0].entryGuardName").value("Bảo Vệ Cổng"));
        mvc.perform(get(API + "/guard/my-activity").session(guardA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inShift").value(true))
                .andExpect(jsonPath("$.checkIns").value(1))
                .andExpect(jsonPath("$.items[0].direction").value("VAO"));

        // Báo sự cố kèm ảnh; chỉ người báo xem được ảnh
        MvcResult reported = post(guardA, "/guard/incidents", """
                {"type":"XE_HU_HONG","plateNumber":"29G-123.45","description":"Xe vỡ gương chiếu hậu",
                 "photoContentType":"image/jpeg","photoBase64":"%s"}""".formatted(Base64.getEncoder().encodeToString(JPEG)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("MOI"))
                .andExpect(jsonPath("$.hasPhoto").value(true))
                .andReturn();
        long incidentId = ((Number) JsonPath.read(reported.getResponse().getContentAsString(), "$.id")).longValue();
        mvc.perform(get(API + "/guard/incidents/" + incidentId + "/photo").session(guardA))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(JPEG));
        mvc.perform(get(API + "/guard/incidents/" + incidentId + "/photo").session(guardB))
                .andExpect(status().isNotFound());

        // Kết thúc ca: số liệu do hệ thống tự đếm
        MvcResult ended = post(guardA, "/guard/shifts/end", "{\"note\":\"Cổng sau hỏng đèn\",\"closingCash\":350000}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.checkIns").value(1))
                .andExpect(jsonPath("$.summary.vehiclesInLot").value(1))
                .andExpect(jsonPath("$.summary.openIncidents").value(1))
                .andExpect(jsonPath("$.closingCash").value(350000))
                .andReturn();
        long shiftId = ((Number) JsonPath.read(ended.getResponse().getContentAsString(), "$.id")).longValue();

        // Bảo vệ B thấy biên bản chờ nhận; chưa xác nhận thì không nhận ca được
        mvc.perform(get(API + "/guard/shifts/current").session(guardB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current").doesNotExist())
                .andExpect(jsonPath("$.pendingHandovers[0].id").value(shiftId))
                .andExpect(jsonPath("$.pendingHandovers[0].guardName").value("Bảo Vệ Cổng"))
                .andExpect(jsonPath("$.openIncidents[0].description").value("Xe vỡ gương chiếu hậu"));
        post(guardB, "/guard/shifts/start", "{\"openingCash\":350000}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HANDOVER_PENDING"));
        post(guardB, "/guard/shifts/start", "{\"openingCash\":300000,\"acknowledgedHandoverIds\":[" + shiftId + "]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expectedOpeningCash").value(350000))
                .andExpect(jsonPath("$.openingDiscrepancy").value(-50000));

        // Chấm công: bảo vệ xem lịch sử trực ca của mình; quản lý xem bảng chấm công theo ngày
        mvc.perform(get(API + "/guard/shifts/history").session(guardA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(shiftId))
                .andExpect(jsonPath("$[0].receivedByName").value("Bảo Vệ Cổng"));
        mvc.perform(get(API + "/manager/guard-shifts").param("from", "2026-11-20").param("to", "2026-11-20")
                        .session(manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shifts.length()").value(2))
                .andExpect(jsonPath("$.totals.length()").value(2))
                .andExpect(jsonPath("$.totals[?(@.cashMismatches == 1)].shifts").value(1));

        // V014: CSDL chặn ca "đã kết thúc" mà thiếu tiền bàn giao / thiếu số liệu (CHECK không còn bỏ lọt NULL)
        assertThatThrownBy(() -> jdbc.sql("UPDATE guard_shifts SET closing_cash = NULL WHERE id = :id")
                .param("id", shiftId).update()).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.sql("UPDATE guard_shifts SET check_ins = NULL WHERE id = :id")
                .param("id", shiftId).update()).isInstanceOf(DataAccessException.class);

        Long receivedBy = jdbc.sql("SELECT received_by FROM guard_shifts WHERE id = :id").param("id", shiftId)
                .query(Long.class).single();
        Long guardBId = jdbc.sql("SELECT id FROM users WHERE username = 'bvgateb'").query(Long.class).single();
        assertThat(receivedBy).isEqualTo(guardBId);
    }

    private org.springframework.test.web.servlet.ResultActions post(MockHttpSession session, String path, String json)
            throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(API + path)
                .contentType(MediaType.APPLICATION_JSON).content(json);
        if (session != null) {
            request.session(session).with(csrf());
        }
        return mvc.perform(request);
    }

    private String createGuard(MockHttpSession manager, String username) throws Exception {
        post(manager, "/manager/guards", """
                {"fullName":"Bảo Vệ Cổng","phone":"0912345678","username":"%s","password":"%s"}"""
                .formatted(username, PASSWORD))
                .andExpect(status().isCreated());
        return username;
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = post(null, "/auth/login", """
                {"username":"%s","password":"%s"}""".formatted(username, password))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
