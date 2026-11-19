package com.ptit.parking.manager.adapter.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

import com.ptit.parking.manager.application.usecase.GetOwnerWalletUseCase;
import com.ptit.parking.manager.application.usecase.GetOwnerWalletUseCase.OwnerWallet;
import com.ptit.parking.shared.security.SessionUser;
import com.ptit.parking.shared.security.WebSecurityTestConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** API #18 xem ví chủ bãi xe; Đợt 3: API #19 "quản lý tự rút điểm" đã bỏ. */
@WebMvcTest(CashWithdrawalController.class)
@Import(WebSecurityTestConfig.class)
class CashWithdrawalControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private GetOwnerWalletUseCase getOwnerWallet;

    @Test
    void xemViChuBaiXe_200() throws Exception {
        when(getOwnerWallet.ownerWallet()).thenReturn(new OwnerWallet("Lê Văn C", 5_000));

        mvc.perform(get("/api/v1/manager/owner-wallet").with(authentication(manager())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Lê Văn C"))
                .andExpect(jsonPath("$.balance").value(5000));
    }

    @Test
    void rutDiemTuDo_daBo_404() throws Exception {
        mvc.perform(post("/api/v1/manager/withdrawals").with(csrf()).with(authentication(manager()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"points\":2000}"))
                .andExpect(status().isNotFound());
    }

    private static UsernamePasswordAuthenticationToken manager() {
        SessionUser principal = () -> 1L;
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("CASH_WITHDRAWAL")));
    }
}
