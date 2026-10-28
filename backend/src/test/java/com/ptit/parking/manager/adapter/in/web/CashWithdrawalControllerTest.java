package com.ptit.parking.manager.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ptit.parking.manager.application.usecase.GetOwnerWalletUseCase;
import com.ptit.parking.manager.application.usecase.GetOwnerWalletUseCase.OwnerWallet;
import com.ptit.parking.manager.application.usecase.WithdrawCashUseCase;
import com.ptit.parking.manager.application.usecase.WithdrawCashUseCase.Command;
import com.ptit.parking.payment.domain.CashWithdrawal;
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

/** API #18, #19 — định dạng JSON, header Idempotency-Key, người thực hiện lấy từ phiên, ví nguồn không từ request. */
@WebMvcTest(CashWithdrawalController.class)
@Import(WebSecurityTestConfig.class)
class CashWithdrawalControllerTest {

    private static final UUID KEY = UUID.fromString("9b2d7e14-3c5a-4f8e-b1d6-2a7c9e0f4b13");
    private static final CashWithdrawal WITHDRAWAL = CashWithdrawal.restore(903L, 2_000, KEY, 1L,
            Instant.parse("2026-10-06T02:00:00.123456Z"), 1L, 3_000, 2_000, new ExchangeRate(1));

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private GetOwnerWalletUseCase getOwnerWallet;

    @MockitoBean
    private WithdrawCashUseCase withdrawCash;

    @Test
    void xemViChuTaiKhoan_200() throws Exception {
        when(getOwnerWallet.ownerWallet()).thenReturn(new OwnerWallet("Lê Văn C", 5_000));

        mvc.perform(get("/api/v1/manager/owner-wallet").with(authentication(manager())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Lê Văn C"))
                .andExpect(jsonPath("$.balance").value(5000));
    }

    @Test
    void rutDiem_201_quanLyTuPhien_boQuaViTrongBody_dungDinhDangDacTa() throws Exception {
        AtomicReference<Command> received = new AtomicReference<>();
        when(withdrawCash.withdraw(any())).thenAnswer(call -> {
            received.set(call.getArgument(0));
            return new WithdrawCashUseCase.Result(WITHDRAWAL, false);
        });

        mvc.perform(withdraw("""
                        {"points":2000,"sourceWalletId":99}
                        """).header("Idempotency-Key", KEY.toString()))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andExpect(jsonPath("$.transactionId").value(903))
                .andExpect(jsonPath("$.points").value(2000))
                .andExpect(jsonPath("$.cashPerPoint").value(1))
                .andExpect(jsonPath("$.cashAmount").value(2000))
                .andExpect(jsonPath("$.balanceBefore").value(5000))
                .andExpect(jsonPath("$.balanceAfter").value(3000))
                .andExpect(jsonPath("$.createdAt").value("2026-10-06T09:00:00+07:00"));

        assertThat(received.get()).isEqualTo(new Command(1L, KEY, 2_000));       // R09: không có ví nào từ request
    }

    @Test
    void guiLaiCungKey_201_kemHeaderIdempotentReplayed() throws Exception {
        when(withdrawCash.withdraw(any())).thenReturn(new WithdrawCashUseCase.Result(WITHDRAWAL, true));

        mvc.perform(withdraw("""
                        {"points":2000}
                        """).header("Idempotency-Key", KEY.toString()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.transactionId").value(903));
    }

    @Test
    void thieuIdempotencyKey_400_khongGoiUseCase() throws Exception {
        mvc.perform(withdraw("""
                        {"points":2000}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        verifyNoInteractions(withdrawCash);
    }

    @Test
    void thieuSoDiem_hoacQua10Trieu_400TaiTruongPoints() throws Exception {
        mvc.perform(withdraw("{}").header("Idempotency-Key", KEY.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0].field").value("points"));
        mvc.perform(withdraw("""
                        {"points":10000001}
                        """).header("Idempotency-Key", KEY.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0].field").value("points"));

        verifyNoInteractions(withdrawCash);
    }

    @Test
    void rutVuotSoDu_422() throws Exception {
        when(withdrawCash.withdraw(any())).thenThrow(new DomainException(ErrorCode.INSUFFICIENT_POINTS));

        mvc.perform(withdraw("""
                        {"points":999999}
                        """).header("Idempotency-Key", KEY.toString()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_POINTS"));
    }

    private static MockHttpServletRequestBuilder withdraw(String body) {
        return post("/api/v1/manager/withdrawals").with(csrf()).with(authentication(manager()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static UsernamePasswordAuthenticationToken manager() {
        SessionUser principal = () -> 1L;
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("CASH_WITHDRAWAL")));
    }
}
