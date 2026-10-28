package com.ptit.parking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
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

/**
 * M5-07 — test TÍCH HỢP: chạy toàn bộ ứng dụng (bảo mật, controller, use case, adapter SQL, Flyway, trigger)
 * trên MySQL 8.4 THẬT trong Docker, không giả lập tầng nào. Kiểm tra flow hoàn chỉnh của đặc tả:
 * sinh viên đăng ký -> quản lý đổi điểm -> bảo vệ cho xe vào -> cho xe ra (tự thanh toán) -> chủ tài khoản nhận điểm.
 * <p>Máy không có Docker thì cả lớp được BỎ QUA (không báo lỗi), các test đơn vị vẫn chạy bình thường.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import(ParkingFlowIntegrationTest.FixedClockConfig.class)
class ParkingFlowIntegrationTest {

    /** Cùng cấu hình với docker-compose.yml: UTF-8, giờ UTC, cho phép user thường tạo trigger (R10). */
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

    /** 10:00 sáng giờ Việt Nam: phí luôn là 3.000 điểm (trước 18:00), test không phụ thuộc giờ chạy. */
    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-28T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    private static final String API = "/api/v1";
    private static final String PASSWORD = "MatKhau@123";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void luongDayDu_dangKy_doiDiem_xeVao_xeRa_chuTaiKhoanNhanDiem_rutDiem() throws Exception {
        long ownerBefore = ownerBalance();

        // 1. Sinh viên tự đăng ký, đăng nhập, đăng ký xe (biển số gõ kiểu nào cũng được chuẩn hóa)
        Student sv = registerStudent();
        MockHttpSession student = login(sv.username(), PASSWORD);
        registerVehicle(student, sv.plate());
        mvc.perform(get(API + "/me/wallet").session(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0));

        // 2. Quản lý đổi 50.000 đ -> 50.000 điểm (R02); chủ tài khoản KHÔNG nhận gì ở bước này
        MockHttpSession manager = login("quanly01", "Quanly@123");
        deposit(manager, sv.code(), 50_000)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.points").value(50000))
                .andExpect(jsonPath("$.balanceAfter").value(50000));
        assertThat(ownerBalance()).isEqualTo(ownerBefore);

        // 3. Quản lý tạo bảo vệ; bảo vệ cho xe vào (R04: chỉ kiểm tra đủ 3.000, KHÔNG trừ điểm)
        MockHttpSession guard = login(createGuard(manager), PASSWORD);
        mvc.perform(get(API + "/guard/check-in/preview").param("plate", sv.plate()).session(guard))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.fullName").value("Sinh Viên Tích Hợp"));
        checkIn(guard, sv.plate()).andExpect(status().isCreated());
        checkIn(guard, sv.plate())                                                   // R05: không vào trùng
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_ALREADY_OPEN"));
        assertThat(balanceOf(sv.username())).isEqualTo(50_000);

        // 4. Xe ra: phí 3.000 theo giờ ra (R03), tự trừ sinh viên + cộng chủ tài khoản trong MỘT transaction
        long sessionId = previewCheckOutSession(guard, sv.plate());
        String key = UUID.randomUUID().toString();
        MvcResult paid = checkOut(guard, sessionId, key)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fee").value(3000))
                .andExpect(jsonPath("$.balanceAfter").value(47000))
                .andReturn();
        long transactionId = longAt(paid, "$.transactionId");

        // Bấm lại cùng Idempotency-Key -> trả lại đúng giao dịch cũ, không trừ lần hai (M3-06)
        checkOut(guard, sessionId, key)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.transactionId").value(transactionId));
        checkOut(guard, sessionId, UUID.randomUUID().toString())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_ALREADY_PAID"));
        assertThat(balanceOf(sv.username())).isEqualTo(47_000);
        assertThat(ownerBalance()).isEqualTo(ownerBefore + 3_000);
        assertThat(jdbc.sql("SELECT status FROM parking_sessions WHERE id = :id").param("id", sessionId)
                .query(String.class).single()).isEqualTo("DA_RA");

        // 5. Chủ tài khoản thấy khoản phí trong lịch sử của mình (+3.000), sinh viên thấy −3.000
        MockHttpSession owner = login("chutk01", "Chutk@123");
        mvc.perform(get(API + "/me/transactions").param("type", "THANH_TOAN").session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(transactionId))
                .andExpect(jsonPath("$.items[0].change").value(3000));
        mvc.perform(get(API + "/me/transactions").session(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("THANH_TOAN"))
                .andExpect(jsonPath("$.items[0].change").value(-3000))
                .andExpect(jsonPath("$.items[1].type").value("DOI_DIEM"))
                .andExpect(jsonPath("$.items[1].change").value(50000));

        // 6. Quản lý rút 3.000 điểm của chủ tài khoản ra tiền mặt (R09)
        mvc.perform(withKey(post(API + "/manager/withdrawals"), UUID.randomUUID()).session(manager)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"points\":3000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cashAmount").value(3000));
        assertThat(ownerBalance()).isEqualTo(ownerBefore);
    }

    @Test
    void khongDuDiem_khongChoXeVao_khongTaoLuotGui() throws Exception {
        Student sv = registerStudent();
        registerVehicle(login(sv.username(), PASSWORD), sv.plate());
        MockHttpSession manager = login("quanly01", "Quanly@123");
        deposit(manager, sv.code(), 2_999).andExpect(status().isCreated());       // thiếu 1 điểm so với 3.000
        MockHttpSession guard = login(createGuard(manager), PASSWORD);

        mvc.perform(get(API + "/guard/check-in/preview").param("plate", sv.plate()).session(guard))
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.reason").value("INSUFFICIENT_POINTS"));
        checkIn(guard, sv.plate())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_POINTS"));

        assertThat(jdbc.sql("""
                        SELECT COUNT(*) FROM parking_sessions ps JOIN vehicles v ON v.id = ps.vehicle_id
                        WHERE v.plate_number = :plate""")
                .param("plate", sv.plate()).query(Long.class).single()).isZero();
        assertThat(balanceOf(sv.username())).isEqualTo(2_999);
    }

    @Test
    void haiBaoVeCungXacNhanXeRa_chiTruDiemDungMotLan() throws Exception {
        Student sv = registerStudent();
        registerVehicle(login(sv.username(), PASSWORD), sv.plate());
        MockHttpSession manager = login("quanly01", "Quanly@123");
        deposit(manager, sv.code(), 10_000).andExpect(status().isCreated());
        MockHttpSession guardA = login(createGuard(manager), PASSWORD);
        MockHttpSession guardB = login(createGuard(manager), PASSWORD);
        checkIn(guardA, sv.plate()).andExpect(status().isCreated());
        long sessionId = previewCheckOutSession(guardA, sv.plate());

        // Hai bảo vệ bấm "Xác nhận cho xe ra" CÙNG LÚC, mỗi người một Idempotency-Key khác nhau
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = List.of(guardA, guardB).stream()
                    .map(guard -> pool.submit(() -> {
                        start.await();
                        return checkOut(guard, sessionId, UUID.randomUUID().toString())
                                .andReturn().getResponse().getStatus();
                    }))
                    .toList();
            start.countDown();
            List<Integer> statuses = List.of(results.get(0).get(), results.get(1).get());
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);              // khóa dòng FOR UPDATE thật của MySQL
        } finally {
            pool.shutdown();
        }

        assertThat(jdbc.sql("SELECT COUNT(*) FROM parking_payment_details WHERE session_id = :id")
                .param("id", sessionId).query(Long.class).single()).isEqualTo(1);
        assertThat(balanceOf(sv.username())).isEqualTo(7_000);
    }

    @Test
    void baoVeBiKhoa_phienDangDungBiChan_dangNhapLaiBiTuChoi() throws Exception {
        MockHttpSession manager = login("quanly01", "Quanly@123");
        String username = createGuard(manager);
        MockHttpSession guard = login(username, PASSWORD);
        long guardId = jdbc.sql("SELECT id FROM users WHERE username = :u").param("u", username)
                .query(Long.class).single();

        mvc.perform(patch(API + "/manager/guards/" + guardId + "/status").session(manager).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"BI_KHOA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BI_KHOA"));

        // Phiên cũ của bảo vệ bị chặn NGAY request kế tiếp (kiểm tra trạng thái ở MỌI request, không đợi hết phiên)
        mvc.perform(get(API + "/guard/check-in/preview").param("plate", "29A12345").session(guard))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        mvc.perform(post(API + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", username, "password", PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void giaoDichDaGhiKhongSuaXoaDuoc_triggerCuaCsdlChan() throws Exception {
        Student sv = registerStudent();
        deposit(login("quanly01", "Quanly@123"), sv.code(), 1_000).andExpect(status().isCreated());
        long id = jdbc.sql("SELECT MAX(id) FROM point_transactions").query(Long.class).single();

        // R10 ở tầng CSDL: kể cả gọi SQL trực tiếp (bỏ qua ứng dụng) cũng không sửa/xóa được sổ cái
        assertThatThrownBy(() -> jdbc.sql("UPDATE point_transactions SET points = 1 WHERE id = :id")
                .param("id", id).update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("R10");
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM point_transactions WHERE id = :id").param("id", id).update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("R10");
        assertThat(jdbc.sql("SELECT points FROM point_transactions WHERE id = :id").param("id", id)
                .query(Long.class).single()).isEqualTo(1_000);
    }

    @Test
    void tongDiemTrongMoiViLuonBangTienDaDoiTruDiemDaRut() {
        // R12 bảo toàn điểm (M3-08): thanh toán chỉ CHUYỂN điểm giữa hai ví, không sinh ra hay mất đi điểm nào.
        // Đúng ở mọi thời điểm, bất kể các test khác đã chạy trước hay chưa.
        long inWallets = jdbc.sql("SELECT COALESCE(SUM(balance), 0) FROM wallets").query(Long.class).single();
        long deposited = jdbc.sql("SELECT COALESCE(SUM(points), 0) FROM point_transactions WHERE type = 'DOI_DIEM'")
                .query(Long.class).single();
        long withdrawn = jdbc.sql("SELECT COALESCE(SUM(points), 0) FROM point_transactions WHERE type = 'RUT_DIEM'")
                .query(Long.class).single();

        assertThat(inWallets).isEqualTo(deposited - withdrawn);
    }

    // ===== Các bước dùng lại =====

    private record Student(String code, String username, String plate) {}

    /** Mỗi lần gọi một sinh viên MỚI (MSSV, tên đăng nhập, biển số không trùng) để các test không đụng nhau. */
    private Student registerStudent() throws Exception {
        int n = 1000 + SEQ.incrementAndGet();
        Student sv = new Student("IT" + n, "svit" + n, "29T" + n + "5");
        mvc.perform(post(API + "/students/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("studentCode", sv.code().toLowerCase(), "fullName", "Sinh Viên Tích Hợp",
                                "username", sv.username(), "password", PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentCode").value(sv.code()));
        return sv;
    }

    private void registerVehicle(MockHttpSession student, String plate) throws Exception {
        String typed = plate.substring(0, 3) + "-" + plate.substring(3);                // "29T-10015" -> "29T10015"
        mvc.perform(post(API + "/me/vehicles").session(student).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("plateNumber", typed, "vehicleTypeCode", "XE_MAY")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plateNumber").value(plate));
    }

    /** Quản lý tạo một bảo vệ mới; trả về tên đăng nhập (mật khẩu = {@link #PASSWORD}). */
    private String createGuard(MockHttpSession manager) throws Exception {
        String username = "bvit" + (1000 + SEQ.incrementAndGet());
        mvc.perform(post(API + "/manager/guards").session(manager).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("fullName", "Bảo Vệ Tích Hợp", "phone", "0912345678",
                                "username", username, "password", PASSWORD)))
                .andExpect(status().isCreated());
        return username;
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post(API + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", username, "password", password)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private ResultActions deposit(MockHttpSession manager, String code, long cash) throws Exception {
        return mvc.perform(withKey(post(API + "/manager/deposits"), UUID.randomUUID()).session(manager)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentCode\":\"" + code + "\",\"cashAmount\":" + cash + "}"));
    }

    private ResultActions checkIn(MockHttpSession guard, String plate) throws Exception {
        return mvc.perform(post(API + "/guard/check-in").session(guard).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json("plateNumber", plate)));
    }

    private long previewCheckOutSession(MockHttpSession guard, String plate) throws Exception {
        MvcResult preview = mvc.perform(get(API + "/guard/check-out/preview").param("plate", plate).session(guard))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.fee").value(3000))
                .andReturn();
        return longAt(preview, "$.sessionId");
    }

    private ResultActions checkOut(MockHttpSession guard, long sessionId, String key) throws Exception {
        return mvc.perform(post(API + "/guard/check-out").session(guard).with(csrf())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content("{\"sessionId\":" + sessionId + "}"));
    }

    private static MockHttpServletRequestBuilder withKey(MockHttpServletRequestBuilder request, UUID key) {
        return request.with(csrf()).header("Idempotency-Key", key.toString());
    }

    private long balanceOf(String username) {
        return jdbc.sql("SELECT w.balance FROM wallets w JOIN users u ON u.id = w.user_id WHERE u.username = :u")
                .param("u", username).query(Long.class).single();
    }

    private long ownerBalance() {
        return balanceOf("chutk01");
    }

    private static long longAt(MvcResult result, String path) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return value.longValue();
    }

    /** JSON phẳng từ các cặp khóa/giá trị chuỗi: json("a", "1", "b", "2") -> {"a":"1","b":"2"}. */
    private static String json(String... pairs) {
        StringBuilder out = new StringBuilder("{");
        for (int i = 0; i < pairs.length; i += 2) {
            out.append(i == 0 ? "" : ",").append('"').append(pairs[i]).append("\":\"").append(pairs[i + 1]).append('"');
        }
        return out.append('}').toString();
    }
}
