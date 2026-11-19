package com.ptit.parking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * Đợt 4b — test TÍCH HỢP trên MySQL thật: migration V017; khiếu nại của sinh viên (kèm ảnh, lượt gửi của chính mình),
 * hoàn phí (tối đa bằng phí đã thu, trỏ về giao dịch gốc), bồi thường do chủ bãi duyệt (thiếu điểm thì rollback, bấm
 * hai lần không trả hai lần), quản lý xử lý sự cố bảo vệ báo, phân ca và đối chiếu đi muộn, phân quyền theo vai trò.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import(SupportIntegrationTest.FixedClockConfig.class)
class SupportIntegrationTest {

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

    /** 10:00 sáng 20/11 giờ Việt Nam: phí 3.000 điểm, ca sáng (06:00–14:00) đang diễn ra. */
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
    void khieuNai_hoanPhi_boiThuong_suCo_phanCa() throws Exception {
        MockHttpSession manager = login("quanly01", "Quanly@123");
        MockHttpSession owner = login("chutk01", "Chutk@123");

        // ===== Chuẩn bị: sinh viên 20.000 điểm, bảo vệ nhận ca lúc 10:00, gửi xe 1 lượt (phí 3.000) =====
        send(null, post(API + "/students/register"), """
                {"studentCode":"KN001","fullName":"Sinh Viên Khiếu Nại","username":"svkn001","password":"%s"}"""
                .formatted(PASSWORD)).andExpect(status().isCreated());
        MockHttpSession student = login("svkn001", PASSWORD);
        send(student, post(API + "/me/vehicles"), "{\"plateNumber\":\"29K12345\",\"vehicleTypeCode\":\"XE_MAY\"}")
                .andExpect(status().isCreated());
        send(manager, keyed(post(API + "/manager/deposits")), "{\"studentCode\":\"KN001\",\"cashAmount\":20000}")
                .andExpect(status().isCreated());
        long guardId = longAt(send(manager, post(API + "/manager/guards"), """
                {"fullName":"Bảo Vệ Phân Ca","phone":"0912345678","username":"bvkn001","password":"%s"}"""
                .formatted(PASSWORD)).andExpect(status().isCreated()).andReturn(), "$.id");
        MockHttpSession guard = login("bvkn001", PASSWORD);
        send(guard, post(API + "/guard/shifts/start"), "{\"openingCash\":0}").andExpect(status().isOk());
        long paymentId = parkOnce(guard);

        // ===== Sinh viên gửi khiếu nại thu phí sai, gắn lượt gửi của chính mình, kèm ảnh =====
        MvcResult sessions = mvc.perform(get(API + "/me/complaint-sessions").session(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fee").value(3000))
                .andReturn();
        long sessionId = longAt(sessions, "$[0].id");
        send(student, post(API + "/me/complaints"),
                "{\"type\":\"THU_PHI_SAI\",\"sessionId\":999999,\"description\":\"Lượt không phải của tôi\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PARKING_SESSION_NOT_FOUND"));
        long feeComplaint = longAt(send(student, post(API + "/me/complaints"), """
                {"type":"THU_PHI_SAI","sessionId":%d,"description":"Tôi ra lúc 10 giờ nhưng bị trừ nhầm",
                 "photoContentType":"image/jpeg","photoBase64":"%s"}"""
                .formatted(sessionId, Base64.getEncoder().encodeToString(JPEG)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("MOI"))
                .andExpect(jsonPath("$.hasPhoto").value(true))
                .andExpect(jsonPath("$.session.plateNumber").value("29K12345"))
                .andReturn(), "$.id");
        mvc.perform(get(API + "/me/complaints/" + feeComplaint + "/photo").session(student))
                .andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"));

        // ===== Quản lý: tiếp nhận, hoàn phí (tối đa bằng phí đã thu), kết luận =====
        mvc.perform(get(API + "/manager/complaints").session(manager))
                .andExpect(jsonPath("$[0].id").value(feeComplaint))
                .andExpect(jsonPath("$[0].studentCode").value("KN001"));
        mvc.perform(get(API + "/manager/complaints/" + feeComplaint + "/photo").session(manager))
                .andExpect(status().isOk());
        send(manager, post(API + "/manager/complaints/" + feeComplaint + "/start"), "")
                .andExpect(jsonPath("$.status").value("DANG_XU_LY"));
        send(manager, post(API + "/manager/complaints/" + feeComplaint + "/resolve"),
                "{\"note\":\"Hoàn\",\"refundPoints\":5000}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REFUND_EXCEEDS_FEE"));
        send(manager, post(API + "/manager/complaints/" + feeComplaint + "/resolve"),
                "{\"note\":\"Đúng là trừ nhầm, hoàn lại phí\",\"refundPoints\":3000}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DA_XU_LY"))
                .andExpect(jsonPath("$.refundTransactionId").isNumber());
        assertThat(balanceOf("svkn001")).isEqualTo(20000);                        // 20.000 − 3.000 + 3.000
        assertThat(jdbc.sql("SELECT original_transaction_id FROM adjustment_details ORDER BY transaction_id DESC LIMIT 1")
                .query(Long.class).single()).isEqualTo(paymentId);                // hoàn phí trỏ về giao dịch gốc (R10)

        // ===== Bồi thường xe hư hỏng: quản lý đề xuất, chủ bãi duyệt =====
        long damage = longAt(send(student, post(API + "/me/complaints"),
                "{\"type\":\"XE_HU_HONG\",\"description\":\"Xe bị đổ, vỡ gương\"}")
                .andExpect(status().isCreated()).andReturn(), "$.id");
        send(manager, post(API + "/manager/complaints/" + damage + "/start"), "").andExpect(status().isOk());
        send(manager, post(API + "/manager/complaints/" + damage + "/propose"),
                "{\"points\":2000,\"note\":\"Camera ghi lại xe bị va trong bãi\"}")
                .andExpect(jsonPath("$.status").value("CHO_DUYET_BOI_THUONG"))
                .andExpect(jsonPath("$.proposedPoints").value(2000));
        // Ví chủ bãi đang 0 điểm (3.000 phí đã hoàn hết) -> không đủ: không có gì thay đổi
        send(owner, post(API + "/owner/compensations/" + damage + "/approve"), "{}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_POINTS"));
        mvc.perform(get(API + "/owner/compensations").session(owner))
                .andExpect(jsonPath("$[0].id").value(damage))
                .andExpect(jsonPath("$[0].status").value("CHO_DUYET_BOI_THUONG"));
        parkOnce(guard);                                                          // chủ bãi có 3.000 điểm
        send(owner, post(API + "/owner/compensations/" + damage + "/approve"), "{\"note\":\"Đồng ý\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(false))
                .andExpect(jsonPath("$.complaint.status").value("DA_BOI_THUONG"))
                .andExpect(jsonPath("$.complaint.deciderName").isNotEmpty());
        send(owner, post(API + "/owner/compensations/" + damage + "/approve"), "{\"note\":\"Đồng ý\"}")
                .andExpect(jsonPath("$.replayed").value(true));                   // bấm lại: không trả lần hai
        assertThat(balanceOf("svkn001")).isEqualTo(20000 - 3000 + 2000);
        assertThat(balanceOf("chutk01")).isEqualTo(1000);
        assertThat(jdbc.sql("SELECT points_balanced FROM v_reconciliation").query(Long.class).single()).isEqualTo(1L);

        // Mất đồ: chủ bãi từ chối bồi thường (bắt buộc lý do)
        long lost = longAt(send(student, post(API + "/me/complaints"),
                "{\"type\":\"MAT_DO\",\"description\":\"Mất mũ bảo hiểm\"}").andReturn(), "$.id");
        send(manager, post(API + "/manager/complaints/" + lost + "/start"), "");
        send(manager, post(API + "/manager/complaints/" + lost + "/propose"), "{\"points\":500,\"note\":\"Đề xuất hỗ trợ\"}");
        send(owner, post(API + "/owner/compensations/" + lost + "/reject"), "{\"note\":\"\"}")
                .andExpect(status().isBadRequest());
        send(owner, post(API + "/owner/compensations/" + lost + "/reject"), "{\"note\":\"Để mũ ngoài khu trông giữ\"}")
                .andExpect(jsonPath("$.status").value("KHONG_BOI_THUONG"));

        // Sinh viên hủy khiếu nại chưa được tiếp nhận; hủy lần hai thì báo xung đột
        long other = longAt(send(student, post(API + "/me/complaints"),
                "{\"type\":\"KHAC\",\"description\":\"Góp ý\"}").andReturn(), "$.id");
        send(student, post(API + "/me/complaints/" + other + "/cancel"), "").andExpect(jsonPath("$.status").value("DA_HUY"));
        send(student, post(API + "/me/complaints/" + other + "/cancel"), "")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COMPLAINT_STATUS_CONFLICT"));
        mvc.perform(get(API + "/me/complaints").session(student)).andExpect(jsonPath("$.length()").value(4));

        // ===== Quản lý xử lý sự cố bảo vệ báo =====
        long incident = longAt(send(guard, post(API + "/guard/incidents"),
                "{\"type\":\"XE_GUI_QUA_LAU\",\"plateNumber\":\"29K12345\",\"description\":\"Xe gửi 3 ngày\"}")
                .andExpect(status().isCreated()).andReturn(), "$.id");
        mvc.perform(get(API + "/manager/incidents").session(manager))
                .andExpect(jsonPath("$[0].id").value(incident))
                .andExpect(jsonPath("$[0].reporterName").value("Bảo Vệ Phân Ca"));
        send(manager, post(API + "/manager/incidents/" + incident + "/start"), "").andExpect(jsonPath("$.status").value("DANG_XU_LY"));
        send(manager, post(API + "/manager/incidents/" + incident + "/resolve"), "{\"note\":\"Đã gọi chủ xe\"}")
                .andExpect(jsonPath("$.status").value("DA_XU_LY"))
                .andExpect(jsonPath("$.handlerName").isNotEmpty());

        // ===== Phân ca: ca sáng hôm nay (đang diễn ra), bảo vệ nhận ca lúc 10:00 -> đi muộn 240 phút =====
        send(manager, post(API + "/manager/shift-plan"),
                "{\"date\":\"2026-11-20\",\"slot\":\"SANG\",\"guardId\":" + guardId + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attendance").value("DI_MUON"))
                .andExpect(jsonPath("$.lateMinutes").value(240))
                .andExpect(jsonPath("$.startsAt").value("2026-11-20T06:00:00+07:00"));
        long morning = longAt(mvc.perform(get(API + "/manager/shift-plan").param("from", "2026-11-20").session(manager))
                .andExpect(jsonPath("$.to").value("2026-11-26"))
                .andExpect(jsonPath("$.shifts.length()").value(1))
                .andReturn(), "$.shifts[0].id");
        send(manager, post(API + "/manager/shift-plan"),
                "{\"date\":\"2026-11-20\",\"slot\":\"SANG\",\"guardId\":" + guardId + "}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SHIFT_ALREADY_ASSIGNED"));
        send(manager, post(API + "/manager/shift-plan"),
                "{\"date\":\"2026-11-19\",\"slot\":\"SANG\",\"guardId\":" + guardId + "}")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("SHIFT_SLOT_PASSED"));
        long tomorrow = longAt(send(manager, post(API + "/manager/shift-plan"),
                "{\"date\":\"2026-11-21\",\"slot\":\"DEM\",\"guardId\":" + guardId + "}")
                .andExpect(jsonPath("$.attendance").value("SAP_TOI"))
                .andExpect(jsonPath("$.endsAt").value("2026-11-22T06:00:00+07:00"))   // ca đêm kết thúc sáng hôm sau
                .andReturn(), "$.id");
        mvc.perform(get(API + "/guard/my-schedule").session(guard))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].attendance").value("DI_MUON"));
        send(manager, delete(API + "/manager/shift-plan/" + tomorrow), "").andExpect(status().isNoContent());
        send(manager, delete(API + "/manager/shift-plan/" + morning), "")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("SHIFT_SLOT_PASSED"));

        // ===== Phân quyền (dữ liệu V017) =====
        mvc.perform(get(API + "/manager/complaints").session(student)).andExpect(status().isForbidden());
        mvc.perform(get(API + "/owner/compensations").session(manager)).andExpect(status().isForbidden());
        mvc.perform(get(API + "/manager/shift-plan").session(guard)).andExpect(status().isForbidden());
        mvc.perform(get(API + "/me/complaints").session(owner)).andExpect(status().isForbidden());

        // Bảo vệ bị khóa thì không xếp ca được
        send(manager, patch(API + "/manager/guards/" + guardId + "/status"), "{\"status\":\"BI_KHOA\"}")
                .andExpect(status().isOk());
        send(manager, post(API + "/manager/shift-plan"),
                "{\"date\":\"2026-11-22\",\"slot\":\"CHIEU\",\"guardId\":" + guardId + "}")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("GUARD_INACTIVE"));
    }

    private long balanceOf(String username) {
        return jdbc.sql("SELECT w.balance FROM wallets w JOIN users u ON u.id = w.user_id WHERE u.username = :u")
                .param("u", username).query(Long.class).single();
    }

    private long parkOnce(MockHttpSession guard) throws Exception {
        send(guard, post(API + "/guard/check-in"), "{\"plateNumber\":\"29K12345\"}").andExpect(status().isCreated());
        MvcResult preview = mvc.perform(get(API + "/guard/check-out/preview").param("plate", "29K12345").session(guard))
                .andReturn();
        MvcResult paid = send(guard, keyed(post(API + "/guard/check-out")),
                "{\"sessionId\":" + longAt(preview, "$.sessionId") + "}").andExpect(status().isCreated()).andReturn();
        return longAt(paid, "$.transactionId");
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
