package com.ptit.parking.manager.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ptit.parking.payment.application.usecase.GetBankConfigUseCase;
import com.ptit.parking.payment.application.usecase.GetBankConfigUseCase.ActiveBankConfig;
import com.ptit.parking.payment.application.usecase.GetBankConfigUseCase.Source;
import com.ptit.parking.payment.application.usecase.UpdateBankConfigUseCase;
import com.ptit.parking.payment.domain.BankAccountConfig;
import com.ptit.parking.shared.security.SessionUser;
import com.ptit.parking.shared.security.WebSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BankConfigController.class)
@Import(WebSecurityTestConfig.class)
class BankConfigControllerTest {

    private static final BankAccountConfig ENV_CFG =
            new BankAccountConfig("MB", "0987654321", "BAI XE HOC VIEN PTIT", "compact2");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private GetBankConfigUseCase getBankConfig;

    @MockitoBean
    private UpdateBankConfigUseCase updateBankConfig;

    @Test
    void quanLyXemCauHinhNganHang_tra200() throws Exception {
        when(getBankConfig.current()).thenReturn(new ActiveBankConfig(ENV_CFG, Source.ENV, null, ENV_CFG));

        mvc.perform(get("/api/v1/manager/bank-config").with(authentication(manager())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankId").value("MB"))
                .andExpect(jsonPath("$.accountNo").value("0987654321"))
                .andExpect(jsonPath("$.source").value("ENV"));
    }

    @Test
    void quanLyCapNhatCauHinhNganHang_tra200() throws Exception {
        BankAccountConfig saved = new BankAccountConfig("VCB", "0123456789", "NGUYEN VAN A", "compact2");
        when(updateBankConfig.update(any(), eq(1L)))
                .thenReturn(new ActiveBankConfig(saved, Source.DATABASE, Instant.parse("2026-11-22T15:00:00Z"), ENV_CFG));

        mvc.perform(put("/api/v1/manager/bank-config")
                        .with(authentication(manager()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bankId":"vcb","accountNo":"0123 456 789","accountName":"Nguyen Van A","template":"compact2"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankId").value("VCB"))
                .andExpect(jsonPath("$.accountNo").value("0123456789"))
                .andExpect(jsonPath("$.source").value("DATABASE"));
    }

    @Test
    void quanLyKhoiPhucMacDinh_tra200() throws Exception {
        when(updateBankConfig.resetToDefault()).thenReturn(new ActiveBankConfig(ENV_CFG, Source.ENV, null, ENV_CFG));

        mvc.perform(delete("/api/v1/manager/bank-config")
                        .with(authentication(manager()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("ENV"));
    }

    private static UsernamePasswordAuthenticationToken manager() {
        SessionUser user = () -> 1L;
        return UsernamePasswordAuthenticationToken.authenticated(
                user, null, List.of(new SimpleGrantedAuthority("CASH_DEPOSIT")));
    }
}
