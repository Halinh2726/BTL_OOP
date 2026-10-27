package com.ptit.parking.manager.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ptit.parking.manager.application.usecase.DepositCashUseCase;
import com.ptit.parking.manager.application.usecase.DepositCashUseCase.Command;
import com.ptit.parking.manager.application.usecase.LookupStudentUseCase;
import com.ptit.parking.manager.application.usecase.LookupStudentUseCase.StudentSummary;
import com.ptit.parking.payment.domain.CashDeposit;
import com.ptit.parking.payment.domain.ExchangeRate;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.shared.security.SessionUser;
import com.ptit.parking.shared.security.WebSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** API #16, #17 — định dạng JSON, header Idempotency-Key, người thực hiện lấy từ phiên. */
@WebMvcTest(CashDepositController.class)
@Import(WebSecurityTestConfig.class)
class CashDepositControllerTest {

    private static final UUID KEY = UUID.fromString("3f1c2b9a-6d4e-4c8b-9a2f-1e5d7c3b8a40");
    private static final String BODY = """
            {"studentCode":"B21DCCN001","cashAmount":10000}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private LookupStudentUseCase lookupStudent;

    @MockitoBean
    private DepositCashUseCase depositCash;

    @Test
    void traCuuSinhVien_200() throws Exception {
        when(lookupStudent.lookup("b21dccn001")).thenReturn(new StudentSummary("B21DCCN001", "Nguyễn Văn A", 0));

        mvc.perform(get("/api/v1/manager/students/b21dccn001").with(authentication(manager())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentCode").value("B21DCCN001"))
                .andExpect(jsonPath("$.fullName").value("Nguyễn Văn A"))
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void traCuuMssvKhongCo_404() throws Exception {
        when(lookupStudent.lookup("B99")).thenThrow(new DomainException(ErrorCode.STUDENT_NOT_FOUND));

        mvc.perform(get("/api/v1/manager/students/B99").with(authentication(manager())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));
    }

    @Test
    void doiDiem_201_quanLyTuPhien_dungDinhDangDacTa() throws Exception {
        AtomicReference<Command> received = new AtomicReference<>();
        when(depositCash.deposit(any())).thenAnswer(call -> {
            received.set(call.getArgument(0));
            return new DepositCashUseCase.Result(savedDeposit(), "B21DCCN001", false);
        });

        mvc.perform(deposit().header("Idempotency-Key", KEY.toString()))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andExpect(jsonPath("$.transactionId").value(901))
                .andExpect(jsonPath("$.studentCode").value("B21DCCN001"))
                .andExpect(jsonPath("$.cashAmount").value(10000))
                .andExpect(jsonPath("$.cashPerPoint").value(1))
                .andExpect(jsonPath("$.points").value(10000))
                .andExpect(jsonPath("$.balanceBefore").value(0))
                .andExpect(jsonPath("$.balanceAfter").value(10000))
                .andExpect(jsonPath("$.createdAt").value("2026-10-05T07:50:00+07:00"));

        assertThat(received.get()).isEqualTo(new Command(1L, KEY, "B21DCCN001", 10_000));
    }

    @Test
    void guiLaiCungKey_201_kemHeaderIdempotentReplayed() throws Exception {
        when(depositCash.deposit(any())).thenReturn(new DepositCashUseCase.Result(savedDeposit(), "B21DCCN001", true));

        mvc.perform(deposit().header("Idempotency-Key", KEY.toString()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.transactionId").value(901));
    }

    @Test
    void thieuHoacSaiIdempotencyKey_400_khongGoiUseCase() throws Exception {
        mvc.perform(deposit())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        mvc.perform(deposit().header("Idempotency-Key", "khong-phai-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        verify(depositCash, never()).deposit(any());
    }

    @Test
    void soTienQua10Trieu_400TaiTruongCashAmount() throws Exception {
        mvc.perform(post("/api/v1/manager/deposits").with(csrf()).with(authentication(manager()))
                        .header("Idempotency-Key", KEY.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentCode":"B21DCCN001","cashAmount":10000001}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0].field").value("cashAmount"));
    }

    @Test
    void cungKeyKhacNoiDung_422() throws Exception {
        when(depositCash.deposit(any())).thenThrow(new DomainException(ErrorCode.IDEMPOTENCY_KEY_REUSED));

        mvc.perform(deposit().header("Idempotency-Key", KEY.toString()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    private static MockHttpServletRequestBuilder deposit() {
        return post("/api/v1/manager/deposits").with(csrf()).with(authentication(manager()))
                .contentType(MediaType.APPLICATION_JSON).content(BODY);
    }

    private static CashDeposit savedDeposit() {
        return CashDeposit.restore(901L, 10_000, KEY, 1L, Instant.parse("2026-10-05T00:50:00Z"),
                5L, 10_000, 10_000, new ExchangeRate(1));
    }

    private static UsernamePasswordAuthenticationToken manager() {
        SessionUser principal = () -> 1L;
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("CASH_DEPOSIT")));
    }
}