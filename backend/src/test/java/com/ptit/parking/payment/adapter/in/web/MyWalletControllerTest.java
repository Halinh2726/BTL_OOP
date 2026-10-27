package com.ptit.parking.payment.adapter.in.web;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ptit.parking.payment.application.usecase.GetMyWalletUseCase;
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.security.SessionUser;
import com.ptit.parking.shared.security.WebSecurityTestConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** API #7 — số dư của CHÍNH người đang đăng nhập. */
@WebMvcTest(MyWalletController.class)
@Import(WebSecurityTestConfig.class)
class MyWalletControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private GetMyWalletUseCase getMyWallet;

    @Test
    void soDuCuaNguoiDangDangNhap() throws Exception {
        when(getMyWallet.walletOf(3L)).thenReturn(new Wallet(10L, 3L, 50_000));

        mvc.perform(get("/api/v1/me/wallet").with(authentication(walletOwner(3L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(50000));
    }

    private static UsernamePasswordAuthenticationToken walletOwner(long id) {
        SessionUser principal = () -> id;
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("WALLET_VIEW_OWN")));
    }
}