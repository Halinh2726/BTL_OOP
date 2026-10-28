package com.ptit.parking.payment.adapter.in.web;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ptit.parking.payment.application.usecase.GetMyTransactionsUseCase;
import com.ptit.parking.payment.application.usecase.GetMyWalletUseCase;
import com.ptit.parking.payment.domain.CashDeposit;
import com.ptit.parking.payment.domain.ExchangeRate;
import com.ptit.parking.payment.domain.ParkingPayment;
import com.ptit.parking.payment.domain.TransactionType;
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.security.SessionUser;
import com.ptit.parking.shared.security.WebSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** API #7, #8 — ví và lịch sử của CHÍNH người đang đăng nhập. */
@WebMvcTest(MyWalletController.class)
@Import(WebSecurityTestConfig.class)
class MyWalletControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private GetMyWalletUseCase getMyWallet;

    @MockitoBean
    private GetMyTransactionsUseCase getMyTransactions;

    @Test
    void soDuCuaNguoiDangDangNhap() throws Exception {
        when(getMyWallet.walletOf(3L)).thenReturn(new Wallet(10L, 3L, 50_000));

        mvc.perform(get("/api/v1/me/wallet").with(authentication(walletOwner(3L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(50000));
    }

    @Test
    void lichSu_dungDinhDangDacTa_dauTheoGocNhinViCuaToi() throws Exception {
        long myWallet = 10L;
        when(getMyTransactions.history(3L, null, null, 20)).thenReturn(new GetMyTransactionsUseCase.Page(myWallet,
                List.of(
                        ParkingPayment.restore(2L, 5_000, UUID.randomUUID(), 4L, Instant.parse("2026-10-05T11:30:00Z"),
                                100L, myWallet, 1L, 5_000, 5_000),
                        CashDeposit.restore(1L, 10_000, UUID.randomUUID(), 1L, Instant.parse("2026-10-05T00:50:00Z"),
                                myWallet, 10_000, 10_000, new ExchangeRate(1))),
                false, null));

        mvc.perform(get("/api/v1/me/transactions").with(authentication(walletOwner(3L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(2))
                .andExpect(jsonPath("$.items[0].type").value("THANH_TOAN"))
                .andExpect(jsonPath("$.items[0].change").value(-5000))
                .andExpect(jsonPath("$.items[0].balanceBefore").value(10000))
                .andExpect(jsonPath("$.items[0].balanceAfter").value(5000))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-10-05T18:30:00+07:00"))
                .andExpect(jsonPath("$.items[1].type").value("DOI_DIEM"))
                .andExpect(jsonPath("$.items[1].change").value(10000))
                .andExpect(jsonPath("$.items[1].balanceBefore").value(0))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextBefore").value(nullValue()));          // đặc tả: "nextBefore": null
    }

    @Test
    void thamSoLocVaPhanTrang_duocChuyenChoUseCase() throws Exception {
        when(getMyTransactions.history(3L, TransactionType.THANH_TOAN, 50L, 5))
                .thenReturn(new GetMyTransactionsUseCase.Page(10L, List.of(), false, null));

        mvc.perform(get("/api/v1/me/transactions").param("type", "THANH_TOAN").param("before", "50").param("limit", "5")
                        .with(authentication(walletOwner(3L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void thamSoSai_400_khongGoiUseCase() throws Exception {
        mvc.perform(get("/api/v1/me/transactions").param("limit", "51").with(authentication(walletOwner(3L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0].field").value("limit"));
        mvc.perform(get("/api/v1/me/transactions").param("type", "KHONG_CO").with(authentication(walletOwner(3L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(getMyTransactions, never()).history(anyLong(), any(), any(), anyInt());
    }

    private static UsernamePasswordAuthenticationToken walletOwner(long id) {
        SessionUser principal = () -> id;
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("WALLET_VIEW_OWN")));
    }
}