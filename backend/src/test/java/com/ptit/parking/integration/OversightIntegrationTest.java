package com.ptit.parking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
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
 * Đợt 4a — test TÍCH HỢP trên MySQL thật: migration V016, sổ giao dịch toàn hệ thống (lọc, phân trang, CSV), hồ sơ
 * sinh viên, đối soát cuối ngày (R12, R13, biên bản không sửa được), thống kê lượt xe theo giờ (giờ Việt Nam),
 * nhật ký tạo / khóa / mở khóa bảo vệ, phân quyền theo vai trò.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import(OversightIntegrationTest.FixedClockConfig.class)
class OversightIntegrationTest {

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
    void soGiaoDich_hoSoSinhVien_doiSoat_thongKe_nhatKyBaoVe() throws Exception {
        MockHttpSession manager = login("quanly01", "Quanly@123");
        MockHttpSession owner = login("chutk01", "Chutk@123");

        // ===== Chuẩn bị: sinh viên đổi 20.000 đ, bảo vệ mới (bị khóa rồi mở lại), gửi xe 1 lượt lúc 10:00 =====
        send(null, post(API + "/students/register"), """
                {"studentCode":"OV001","fullName":"Sinh Viên Giám Sát","username":"svov001","password":"%s"}"""
                .formatted(PASSWORD)).andExpect(status().isCreated());
        MockHttpSession student = login("svov001", PASSWORD);
        send(student, post(API + "/me/vehicles"), "{\"plateNumber\":\"29G12345\",\"vehicleTypeCode\":\"XE_MAY\"}")
                .andExpect(status().isCreated());
        send(manager, keyed(post(API + "/manager/deposits")), "{\"studentCode\":\"OV001\",\"cashAmount\":20000}")
                .andExpect(status().isCreated());

        MvcResult created = send(manager, post(API + "/manager/guards"), """
                {"fullName":"Bảo Vệ Giám Sát","phone":"0912345678","username":"bvov001","password":"%s"}"""
                .formatted(PASSWORD)).andExpect(status().isCreated()).andReturn();
        long guardId = longAt(created, "$.id");
        send(manager, patch(API + "/manager/guards/" + guardId + "/status"), "{\"status\":\"BI_KHOA\"}")
                .andExpect(status().isOk());
        send(manager, patch(API + "/manager/guards/" + guardId + "/status"), "{\"status\":\"HOAT_DONG\"}")
                .andExpect(status().isOk());
        long managerId = jdbc.sql("SELECT id FROM users WHERE username = 'quanly01'").query(Long.class).single();
        List<String> actions = jdbc.sql("SELECT action FROM audit_logs WHERE target_user_id = :id AND actor_id = :m ORDER BY id")
                .param("id", guardId).param("m", managerId).query(String.class).list();
        assertThat(actions).containsExactly("TAO_BAO_VE", "KHOA_TAI_KHOAN", "MO_KHOA_TAI_KHOAN");

        MockHttpSession guard = login("bvov001", PASSWORD);
        send(guard, post(API + "/guard/check-in"), "{\"plateNumber\":\"29G12345\"}").andExpect(status().isCreated());
        MvcResult preview = mvc.perform(get(API + "/guard/check-out/preview").param("plate", "29G12345").session(guard))
                .andReturn();
        send(guard, keyed(post(API + "/guard/check-out")), "{\"sessionId\":" + longAt(preview, "$.sessionId") + "}")
                .andExpect(status().isCreated());

        // ===== Sổ giao dịch: lọc theo MSSV (chữ thường vẫn được), loại; phân trang =====
        mvc.perform(get(API + "/manager/ledger").param("studentCode", "ov001").session(manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].type").value("THANH_TOAN"))
                .andExpect(jsonPath("$.items[0].points").value(3000))
                .andExpect(jsonPath("$.items[0].plateNumber").value("29G12345"))
                .andExpect(jsonPath("$.items[0].from.studentCode").value("OV001"))
                .andExpect(jsonPath("$.items[0].to.role").value("CHU_TAI_KHOAN"))
                .andExpect(jsonPath("$.items[0].performerName").value("Bảo Vệ Giám Sát"))
                .andExpect(jsonPath("$.items[1].type").value("DOI_DIEM"))
                .andExpect(jsonPath("$.items[1].cashAmount").value(20000))
                .andExpect(jsonPath("$.items[1].from").doesNotExist())
                .andExpect(jsonPath("$.items[1].performerRole").value("QUAN_LY"))
                .andExpect(jsonPath("$.items[1].createdAt").value("2026-11-20T10:00:00+07:00"));
        MvcResult firstPage = mvc.perform(get(API + "/manager/ledger").param("studentCode", "OV001").param("limit", "1")
                        .session(manager))
                .andExpect(jsonPath("$.hasMore").value(true)).andReturn();
        mvc.perform(get(API + "/manager/ledger").param("studentCode", "OV001").param("limit", "1")
                        .param("before", String.valueOf(longAt(firstPage, "$.nextBefore"))).session(manager))
                .andExpect(jsonPath("$.items[0].type").value("DOI_DIEM"))
                .andExpect(jsonPath("$.hasMore").value(false));
        mvc.perform(get(API + "/manager/ledger").param("type", "DOI_DIEM").param("from", "2026-11-21").session(manager))
                .andExpect(jsonPath("$.items.length()").value(0));                    // ngày mai: chưa có gì

        MvcResult csv = mvc.perform(get(API + "/manager/ledger/export").param("from", "2026-11-20")
                        .param("to", "2026-11-20").param("studentCode", "OV001").session(manager))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andReturn();
        String body = new String(csv.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(body).startsWith("﻿Mã GD,Thời gian,Loại")
                .contains("20/11/2026 10:00:00,Thanh toán phí gửi xe,3000")
                .contains("Đổi điểm,20000,20000");
        mvc.perform(get(API + "/manager/ledger/export").param("from", "2026-01-01").param("to", "2026-11-20")
                        .session(manager))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DATE_RANGE_INVALID"));

        // ===== Hồ sơ sinh viên =====
        mvc.perform(get(API + "/manager/student-profiles").param("q", "giam sat").session(manager))   // không dấu vẫn tìm được
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentCode").value("OV001"))
                .andExpect(jsonPath("$[0].balance").value(17000))
                .andExpect(jsonPath("$[0].activeVehicles").value(1));
        mvc.perform(get(API + "/manager/student-profiles/ov001").session(manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.student.fullName").value("Sinh Viên Giám Sát"))
                .andExpect(jsonPath("$.vehicles[0].plateNumber").value("29G12345"))
                .andExpect(jsonPath("$.vehicles[0].parkedNow").value(false))
                .andExpect(jsonPath("$.recentSessions[0].fee").value(3000))
                .andExpect(jsonPath("$.recentTransactions.length()").value(2));

        // ===== Đối soát cuối ngày =====
        mvc.perform(get(API + "/manager/reconciliations/2026-11-20").session(manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openingCash").value(0))
                .andExpect(jsonPath("$.cashIn").value(20000))
                .andExpect(jsonPath("$.expectedCash").value(20000))           // R13
                .andExpect(jsonPath("$.payments").value(1))
                .andExpect(jsonPath("$.revenue").value(3000))
                .andExpect(jsonPath("$.pointsBalanced").value(true))          // R12
                .andExpect(jsonPath("$.closed").doesNotExist());
        send(manager, post(API + "/manager/reconciliations"), "{\"date\":\"2026-11-20\",\"countedCash\":19000}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RECONCILIATION_NOTE_REQUIRED"));
        send(manager, post(API + "/manager/reconciliations"),
                "{\"date\":\"2026-11-20\",\"countedCash\":19000,\"note\":\"Trả nhầm 1.000 đ tiền thừa\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("THIEU"))
                .andExpect(jsonPath("$.discrepancy").value(-1000))
                .andExpect(jsonPath("$.closedByName").isNotEmpty());
        send(manager, post(API + "/manager/reconciliations"), "{\"date\":\"2026-11-20\",\"countedCash\":20000}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_RECONCILED"));
        mvc.perform(get(API + "/manager/reconciliations/2026-11-21").session(manager))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RECONCILIATION_DATE_INVALID"));
        mvc.perform(get(API + "/manager/reconciliations").session(manager))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].note").value("Trả nhầm 1.000 đ tiền thừa"));
        assertThatThrownBy(() -> jdbc.sql("UPDATE daily_reconciliations SET counted_cash = 20000").update())
                .hasMessageContaining("chi cho phep them moi");                // biên bản không sửa được

        // ===== Thống kê lượt xe: chủ bãi xem được, giờ Việt Nam =====
        mvc.perform(get(API + "/stats/traffic").param("days", "7").session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-11-14"))
                .andExpect(jsonPath("$.hours.length()").value(24))
                .andExpect(jsonPath("$.hours[10].todayEntries").value(1))
                .andExpect(jsonPath("$.hours[10].todayExits").value(1))
                .andExpect(jsonPath("$.totalEntries").value(1))
                .andExpect(jsonPath("$.peakHours[0]").value(10))
                .andExpect(jsonPath("$.parkedNow").value(0));

        // ===== Phân quyền theo vai trò (dữ liệu V016) =====
        mvc.perform(get(API + "/manager/ledger").session(owner)).andExpect(status().isForbidden());
        mvc.perform(get(API + "/manager/student-profiles/OV001").session(guard)).andExpect(status().isForbidden());
        mvc.perform(get(API + "/stats/traffic").session(guard)).andExpect(status().isForbidden());
        mvc.perform(get(API + "/manager/reconciliations").session(student)).andExpect(status().isForbidden());
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
