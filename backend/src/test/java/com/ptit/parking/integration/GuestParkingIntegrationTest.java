package com.ptit.parking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ptit.parking.guest.application.GuestParkingStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import(GuestParkingIntegrationTest.TestTime.class)
class GuestParkingIntegrationTest {
    private static final String API = "/api/v1";
    private static final String GATE = "/gate/guest";
    private static final String DAY = "2026-11-20";
    private static final String PASSWORD = "MatKhau@123";

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
    static class TestTime {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(new AtomicReference<>(Instant.parse("2026-11-20T03:00:00Z")),
                    ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;
        private final ZoneId zone;

        MutableClock(AtomicReference<Instant> instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        void moveTo(String time) {
            instant.set(Instant.parse(time));
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired GuestParkingStore store;
    @Autowired MutableClock clock;

    @Test
    void guestParkingKeepsTicketsCashVietQrAndStudentLedgerConsistent() throws Exception {
        MockHttpSession manager = login("quanly01", "Quanly@123");
        MockHttpSession owner = login("chutk01", "Chutk@123");
        json(put(API + "/manager/fee-rules/XE_DAP").param("customerType", "KHACH"), manager, """
                {"rules":[{"upToMinutes":120,"feePoints":2100},{"upToMinutes":360,"feePoints":3000},
                          {"upToMinutes":1440,"feePoints":5000}]}
                """).andExpect(status().isOk());
        mvc.perform(get(API + "/manager/fee-rules").session(manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedules[?(@.vehicleTypeCode == 'XE_DAP')].rules[0].feePoints").value(1500));
        json(post(API + "/manager/guards"), manager, """
                {"fullName":"Bảo Vệ Khách","phone":"0912345678","username":"bvkhach","password":"%s"}
                """.formatted(PASSWORD)).andExpect(status().isCreated());
        MockHttpSession guard = login("bvkhach", PASSWORD);
        json(post(API + "/guard/shifts/start"), guard, "{\"openingCash\":100000}").andExpect(status().isOk());

        json(post(API + "/students/register"), null, """
                {"studentCode":"GUEST01","fullName":"Sinh Viên","username":"svguest1","password":"%s"}
                """.formatted(PASSWORD)).andExpect(status().isCreated());
        MockHttpSession student = login("svguest1", PASSWORD);
        json(post(API + "/me/vehicles"), student,
                "{\"plateNumber\":\"29B1-123.45\",\"vehicleTypeCode\":\"XE_MAY\"}").andExpect(status().isCreated());
        String studentPlate = "{\"plateNumber\":\"29B1-123.45\",\"vehicleTypeCode\":\"XE_MAY\"}";
        json(post(API + GATE + "/check-in"), student, studentPlate).andExpect(status().isForbidden());
        json(post(API + GATE + "/check-in"), guard, studentPlate).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("GUEST_PLATE_BELONGS_TO_STUDENT"));

        String firstPlate = "29C1-123.45";
        String firstEntry = """
                {"plateNumber":"%s","vehicleTypeCode":"XE_MAY","guestName":"Khách thử","guestPhone":"0901234567","note":"Phụ huynh"}
                """.formatted(firstPlate);
        json(post(API + GATE + "/check-in/preview"), guard, firstEntry).andExpect(status().isOk())
                .andExpect(jsonPath("$.rules[0].feePoints").value(5000));
        MvcResult ticket = json(post(API + GATE + "/check-in"), guard, firstEntry)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.ticketCode").value("K-261120-0001")).andReturn();
        int firstId = read(ticket, "$.sessionId");
        String token = read(ticket, "$.ticketToken");
        assertThat(token).matches("[0-9a-f]{32}");
        json(post(API + GATE + "/check-in"), guard, firstEntry).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUEST_SESSION_ALREADY_OPEN"));
        mvc.perform(get(API + "/public/guest-tickets/" + token)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.amount").value(5000))
                .andExpect(jsonPath("$.guestPhone").doesNotExist())
                .andExpect(jsonPath("$.plateNumber").doesNotExist());
        mvc.perform(get(API + "/public/guest-tickets/K-261120-0001")).andExpect(status().isNotFound());
        json(post(API + "/me/vehicles"), student,
                "{\"plateNumber\":\"29C1-123.45\",\"vehicleTypeCode\":\"XE_MAY\"}").andExpect(status().isCreated());
        json(post(API + "/guard/check-in"), guard, "{\"plateNumber\":\"29C112345\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GUEST_SESSION_ALREADY_OPEN"));

        MvcResult qrTicket = json(post(API + GATE + "/check-in"), guard,
                "{\"plateNumber\":\"30B1-123.45\",\"vehicleTypeCode\":\"XE_MAY\"}")
                .andExpect(status().isCreated()).andReturn();
        int qrId = read(qrTicket, "$.sessionId");
        String qrCode = read(qrTicket, "$.ticketCode");
        MvcResult bicycle = json(post(API + GATE + "/check-in"), guard, "{\"vehicleTypeCode\":\"XE_DAP\"}")
                .andExpect(status().isCreated()).andReturn();
        int bicycleId = read(bicycle, "$.sessionId");
        mvc.perform(get(API + GATE + "/parked").session(guard)).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3)).andExpect(jsonPath("$.items[0].ticketToken").doesNotExist());
        json(put(API + "/manager/guest-config"), manager, "{\"enabled\":false,\"lostTicketFeeVnd\":20000}")
                .andExpect(status().isOk());
        json(post(API + GATE + "/check-in"), guard, "{\"vehicleTypeCode\":\"XE_DAP\"}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("GUEST_PARKING_DISABLED"));

        String cash = """
                {"sessionId":%d,"ticket":{"ticketToken":"%s"},"observedPlate":"%s","expectedAmount":5000}
                """.formatted(firstId, token, firstPlate);
        pay(guard, "cash", UUID.randomUUID(), cash.replace(firstPlate, "29C1-333.45"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("GUEST_TICKET_PLATE_MISMATCH"));
        UUID key = UUID.randomUUID();
        pay(guard, "cash", key, cash).andExpect(status().isCreated()).andExpect(jsonPath("$.amount").value(5000));
        pay(guard, "cash", key, cash).andExpect(status().isCreated()).andExpect(header().string("Idempotent-Replayed", "true"));
        pay(guard, "cash", UUID.randomUUID(), cash).andExpect(jsonPath("$.code").value("SESSION_ALREADY_PAID"));

        clock.moveTo("2026-11-20T04:59:00Z");
        String qrRequest = "{\"ticket\":{\"ticketCode\":\"%s\"},\"observedPlate\":\"30B112345\"}".formatted(qrCode);
        MvcResult quote = json(post(API + GATE + "/sessions/" + qrId + "/vietqr"), guard, qrRequest)
                .andExpect(status().isOk()).andExpect(jsonPath("$.order.transferContent").value(qrCode))
                .andExpect(jsonPath("$.order.fee").value(5000)).andReturn();
        String quoteId = read(quote, "$.quoteId");
        clock.moveTo("2026-11-20T05:01:00Z");
        String bankPayment = """
                {"sessionId":%d,"ticket":{"ticketCode":"%s"},"observedPlate":"30B112345","expectedAmount":5000,"quoteId":"%s"}
                """.formatted(qrId, qrCode, quoteId);
        pay(guard, "vietqr", UUID.randomUUID(), bankPayment).andExpect(status().isCreated())
                .andExpect(jsonPath("$.method").value("VIETQR")).andExpect(jsonPath("$.amount").value(5000));

        String lost = "{\"sessionId\":%d,\"documentLast4\":\"1234\",\"expectedAmount\":23000}".formatted(bicycleId);
        pay(guard, "lost-ticket", UUID.randomUUID(), lost).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("GUEST_LOST_TICKET_REQUIRES_MANAGER"));
        mvc.perform(post(API + "/manager/guest-sessions/" + bicycleId + "/approve-lost-ticket")
                .session(manager).with(csrf())).andExpect(status().isNoContent());
        pay(guard, "lost-ticket", UUID.randomUUID(), lost).andExpect(status().isCreated())
                .andExpect(jsonPath("$.lostTicketFee").value(20000));
        mvc.perform(get(API + "/guard/incidents").session(guard)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("MAT_VE"));

        mvc.perform(get(API + "/guard/cash-sheet").session(guard)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCollected").value(28000))
                .andExpect(jsonPath("$.expectedClosingCash").value(128000))
                .andExpect(jsonPath("$.entries[0].plateDisplay").value("Không biển"));
        mvc.perform(get(API + "/owner/guest-revenue").session(owner).param("from", DAY).param("to", DAY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payments").value(3))
                .andExpect(jsonPath("$.cash").value(28000)).andExpect(jsonPath("$.vietqr").value(5000));
        mvc.perform(get(API + "/manager/guest-sessions/export").session(manager).param("from", DAY).param("to", DAY))
                .andExpect(status().isOk()).andExpect(header().exists("Content-Disposition"));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM point_transactions").query(Long.class).single()).isZero();
        assertThatThrownBy(() -> jdbc.sql("UPDATE guest_payments SET amount = 1").update()).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM guest_payments").update()).isInstanceOf(DataAccessException.class);
        store.anonymizeBefore(clock.instant());
        assertThat(jdbc.sql("SELECT COUNT(*) FROM guest_sessions WHERE guest_phone IS NOT NULL OR note IS NOT NULL")
                .query(Long.class).single()).isZero();

        json(put(API + "/manager/guest-config"), manager, "{\"enabled\":true,\"lostTicketFeeVnd\":20000}")
                .andExpect(status().isOk());
        clock.moveTo("2026-11-20T14:00:00Z");
        guard = login("bvkhach", PASSWORD);
        MvcResult overnight = json(post(API + GATE + "/check-in"), guard, "{\"vehicleTypeCode\":\"XE_DAP\"}")
                .andExpect(status().isCreated()).andReturn();
        int overnightId = read(overnight, "$.sessionId");
        String overnightToken = read(overnight, "$.ticketToken");
        clock.moveTo("2026-11-20T15:15:00Z");
        String afterCurfew = """
                {"sessionId":%d,"ticket":{"ticketToken":"%s"},"expectedAmount":2100}
                """.formatted(overnightId, overnightToken);
        pay(guard, "cash", UUID.randomUUID(), afterCurfew).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CHECKOUT_CURFEW"));
        clock.moveTo("2026-11-21T01:00:00Z");
        mvc.perform(get(API + "/public/guest-tickets/" + overnightToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.overnight").value(true)).andExpect(jsonPath("$.amount").value(15000));
    }

    private ResultActions pay(MockHttpSession guard, String method, UUID key, String body) throws Exception {
        return json(post(API + GATE + "/check-out/" + method).header("Idempotency-Key", key), guard, body);
    }

    private MockHttpSession login(String username, String password) throws Exception {
        var result = json(post(API + "/auth/login"), null,
                "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private ResultActions json(MockHttpServletRequestBuilder request, MockHttpSession session, String content) throws Exception {
        if (session != null) {
            request.session(session).with(csrf());
        }
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(content));
    }

    private static <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }
}
