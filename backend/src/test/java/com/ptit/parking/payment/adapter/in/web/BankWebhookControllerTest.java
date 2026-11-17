package com.ptit.parking.payment.adapter.in.web;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ptit.parking.payment.application.usecase.ProcessBankWebhookUseCase;
import com.ptit.parking.payment.application.usecase.ProcessBankWebhookUseCase.ProcessResult;
import com.ptit.parking.shared.security.WebSecurityTestConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Kiểm thử tầng web cho {@link BankWebhookController} (xác thực API Key, tiếp nhận webhook SePay/Casso).
 */
@WebMvcTest(BankWebhookController.class)
@Import(WebSecurityTestConfig.class)
class BankWebhookControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProcessBankWebhookUseCase processWebhook;

    @Test
    void sepayWebhook_dungApiKey_traVe200VaKetQua() throws Exception {
        when(processWebhook.process(anyList())).thenReturn(List.of(
                new ProcessResult("FT24084998242", "NAP", "SUCCESS",
                        "Nạp thành công 50000đ", 50000L, "B21DCCN001", null, false)));

        String payload = """
                {
                    "id": 123456,
                    "gateway": "MBBank",
                    "content": "NAP B21DCCN001",
                    "transferAmount": 50000,
                    "referenceCode": "FT24084998242"
                }
                """;

        mvc.perform(post("/api/v1/webhooks/sepay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Apikey sepay_secret_key_123456")
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.results[0].action").value("NAP"))
                .andExpect(jsonPath("$.results[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.results[0].studentCode").value("B21DCCN001"));

        verify(processWebhook).process(anyList());
    }

    @Test
    void sepayWebhook_saiApiKey_traVe401() throws Exception {
        String payload = """
                {
                    "id": 123456,
                    "gateway": "MBBank",
                    "content": "NAP B21DCCN001",
                    "transferAmount": 50000,
                    "referenceCode": "FT24084998242"
                }
                """;

        mvc.perform(post("/api/v1/webhooks/sepay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Apikey sai_khoa_bi_mat")
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void cassoWebhook_dungSecureToken_traVe200() throws Exception {
        when(processWebhook.process(anyList())).thenReturn(List.of(
                new ProcessResult("MB123", "TTGX", "SUCCESS",
                        "Thanh toán thành công", 3000L, "B21DCCN001", 42L, false)));

        String payload = """
                {
                    "error": 0,
                    "data": [
                        {
                            "id": 101,
                            "tid": "MB123",
                            "description": "TTGX 42",
                            "amount": 3000,
                            "bankName": "MBBank"
                        }
                    ]
                }
                """;

        mvc.perform(post("/api/v1/webhooks/casso")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("secure-token", "sepay_secret_key_123456")
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.results[0].action").value("TTGX"))
                .andExpect(jsonPath("$.results[0].status").value("SUCCESS"));
    }
}

