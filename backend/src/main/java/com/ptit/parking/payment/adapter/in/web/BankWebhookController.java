package com.ptit.parking.payment.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ptit.parking.payment.application.usecase.ProcessBankWebhookUseCase;
import com.ptit.parking.payment.application.usecase.ProcessBankWebhookUseCase.BankTransactionItem;
import com.ptit.parking.payment.application.usecase.ProcessBankWebhookUseCase.ProcessResult;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller tiếp nhận Webhook ngân hàng tự động (SePay, Casso, OpenBanking).
 * Bảo vệ bằng Webhook API Key qua header:
 * - {@code Authorization: Apikey <KEY>} hoặc {@code Bearer <KEY>}
 * - {@code X-API-KEY: <KEY>}
 * - {@code secure-token: <KEY>} (chuẩn Casso)
 */
@RestController
@RequestMapping("/api/v1/webhooks")
public class BankWebhookController {

    private static final Logger log = LoggerFactory.getLogger(BankWebhookController.class);

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SepayWebhookRequest(
            Long id,
            String gateway,
            String transactionDate,
            String accountNumber,
            String code,
            String content,
            String transferType,
            Long transferAmount,
            Long accumulated,
            String subAccount,
            String referenceCode,
            String description,
            Long amount
    ) {
        public BankTransactionItem toItem() {
            long amt = transferAmount != null ? transferAmount : (amount != null ? amount : 0L);
            String text = (content != null && !content.isBlank()) ? content : (description != null ? description : "");
            String ref = (referenceCode != null && !referenceCode.isBlank())
                    ? referenceCode
                    : (id != null ? String.valueOf(id) : "");
            String type = (transferType != null && !transferType.isBlank()) ? transferType : "in";
            return new BankTransactionItem(gateway, amt, text, ref, type);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CassoWebhookRequest(
            Integer error,
            List<CassoTransaction> data
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record CassoTransaction(
                Long id,
                String tid,
                String description,
                Long amount,
                @JsonProperty("bank_sub_acc_id") String bankSubAccId,
                String bankName
        ) {
            public BankTransactionItem toItem() {
                long amt = amount != null ? amount : 0L;
                String text = description != null ? description : "";
                String ref = (tid != null && !tid.isBlank()) ? tid : (id != null ? String.valueOf(id) : "");
                return new BankTransactionItem(bankName, amt, text, ref, "in");
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GenericTransactionDto(
            String gateway,
            Long amount,
            String content,
            String description,
            String referenceCode,
            String tid,
            String transferType
    ) {
        public BankTransactionItem toItem() {
            long amt = amount != null ? amount : 0L;
            String text = (content != null && !content.isBlank()) ? content : (description != null ? description : "");
            String ref = (referenceCode != null && !referenceCode.isBlank()) ? referenceCode : (tid != null ? tid : "");
            String type = (transferType != null && !transferType.isBlank()) ? transferType : "in";
            return new BankTransactionItem(gateway, amt, text, ref, type);
        }
    }

    public record WebhookResponse(
            boolean success,
            String message,
            List<ProcessResult> results
    ) {}

    private final ProcessBankWebhookUseCase processWebhook;
    private final String expectedApiKey;

    public BankWebhookController(
            ProcessBankWebhookUseCase processWebhook,
            @Value("${parking.webhook.api-key:sepay_secret_key_123456}") String expectedApiKey) {
        this.processWebhook = processWebhook;
        this.expectedApiKey = expectedApiKey;
    }

    /**
     * Endpoint nhận webhook từ SePay (https://my.sepay.vn).
     */
    @PostMapping("/sepay")
    public WebhookResponse handleSepayWebhook(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-API-KEY", required = false) String xApiKey,
            @RequestBody SepayWebhookRequest request) {
        verifyApiKey(authHeader, xApiKey, null);
        log.info("Nhận SePay webhook: id={}, content='{}', amount={}", request.id(), request.content(), request.transferAmount());
        List<ProcessResult> results = processWebhook.process(List.of(request.toItem()));
        return new WebhookResponse(true, "Xử lý thành công", results);
    }

    /**
     * Endpoint nhận webhook từ Casso (https://casso.vn).
     */
    @PostMapping("/casso")
    public WebhookResponse handleCassoWebhook(
            @RequestHeader(value = "secure-token", required = false) String secureToken,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody CassoWebhookRequest request) {
        verifyApiKey(authHeader, null, secureToken);
        List<BankTransactionItem> items = request.data() != null
                ? request.data().stream().map(CassoWebhookRequest.CassoTransaction::toItem).toList()
                : List.of();
        log.info("Nhận Casso webhook: số lượng giao dịch = {}", items.size());
        List<ProcessResult> results = processWebhook.process(items);
        return new WebhookResponse(true, "Xử lý thành công", results);
    }

    /**
     * Endpoint webhook ngân hàng dùng chung (Generic Payment Webhook).
     */
    @PostMapping({"/payment", "/bank"})
    public WebhookResponse handleGenericWebhook(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-API-KEY", required = false) String xApiKey,
            @RequestBody List<GenericTransactionDto> request) {
        verifyApiKey(authHeader, xApiKey, null);
        List<BankTransactionItem> items = request.stream().map(GenericTransactionDto::toItem).toList();
        List<ProcessResult> results = processWebhook.process(items);
        return new WebhookResponse(true, "Xử lý thành công", results);
    }

    private void verifyApiKey(String authHeader, String xApiKey, String secureToken) {
        if (expectedApiKey == null || expectedApiKey.isBlank()) {
            return;
        }

        String token = null;
        if (xApiKey != null && !xApiKey.isBlank()) {
            token = xApiKey.strip();
        } else if (secureToken != null && !secureToken.isBlank()) {
            token = secureToken.strip();
        } else if (authHeader != null && !authHeader.isBlank()) {
            String trimmed = authHeader.strip();
            if (trimmed.regionMatches(true, 0, "Apikey ", 0, 7)) {
                token = trimmed.substring(7).strip();
            } else if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
                token = trimmed.substring(7).strip();
            } else {
                token = trimmed;
            }
        }

        if (token == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                expectedApiKey.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Webhook xác thực thất bại: token không trùng khớp");
            throw new DomainException(ErrorCode.UNAUTHENTICATED);
        }
    }
}

