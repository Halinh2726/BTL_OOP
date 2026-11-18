package com.ptit.parking.payment.application.usecase;

import com.ptit.parking.auth.application.port.out.UserRepository;
import com.ptit.parking.auth.domain.User;
import com.ptit.parking.manager.application.usecase.DepositCashUseCase;
import com.ptit.parking.parking.application.usecase.PayParkingFeeByVietQrUseCase;
import com.ptit.parking.shared.domain.DomainException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Xử lý thông báo chuyển khoản tự động (Webhook từ SePay / Casso / Ngân hàng).
 * Hỗ trợ 2 luồng tự động:
 * 1. Nạp điểm tự động vào ví sinh viên: cú pháp "NAP <MSSV>" -> nạp tiền mặt và cộng điểm ví.
 * 2. Thanh toán đơn gửi xe trực tiếp: cú pháp "TTGX <SESSION_ID>" -> đóng lượt gửi và cho xe ra.
 */
public class ProcessBankWebhookUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessBankWebhookUseCase.class);

    private static final Pattern NAP_PATTERN = Pattern.compile("(?i)\\bNAP\\s*([A-Za-z0-9]+)\\b");
    private static final Pattern TTGX_PATTERN = Pattern.compile("(?i)\\bTTGX\\s*(\\d+)\\b");

    public record BankTransactionItem(
            String gateway,
            long amount,
            String content,
            String referenceCode,
            String transferType
    ) {}

    public record ProcessResult(
            String referenceCode,
            String action,
            String status,
            String message,
            Long amount,
            String studentCode,
            Long sessionId,
            boolean replayed
    ) {}

    private final DepositCashUseCase depositCash;
    private final PayParkingFeeByVietQrUseCase payParkingFeeByVietQr;
    private final UserRepository users;

    public ProcessBankWebhookUseCase(DepositCashUseCase depositCash,
                                     PayParkingFeeByVietQrUseCase payParkingFeeByVietQr,
                                     UserRepository users) {
        this.depositCash = depositCash;
        this.payParkingFeeByVietQr = payParkingFeeByVietQr;
        this.users = users;
    }

    public List<ProcessResult> process(List<BankTransactionItem> items) {
        List<ProcessResult> results = new ArrayList<>();
        long performerId = users.findByUsername("quanly01").map(User::id).orElse(1L);

        for (BankTransactionItem item : items) {
            results.add(processSingle(item, performerId));
        }
        return results;
    }

    private ProcessResult processSingle(BankTransactionItem item, long performerId) {
        String ref = item.referenceCode() != null && !item.referenceCode().isBlank()
                ? item.referenceCode().strip()
                : UUID.randomUUID().toString();

        if (item.transferType() != null && !item.transferType().isBlank() && !item.transferType().equalsIgnoreCase("in")) {
            log.info("Bỏ qua giao dịch không phải chiều vào (in): ref={}, type={}", ref, item.transferType());
            return new ProcessResult(ref, "NONE", "SKIPPED", "Bỏ qua giao dịch không phải chiều vào",
                    item.amount(), null, null, false);
        }

        if (item.amount() <= 0) {
            log.warn("Bỏ qua giao dịch số tiền <= 0: ref={}, amount={}", ref, item.amount());
            return new ProcessResult(ref, "NONE", "SKIPPED", "Số tiền giao dịch phải lớn hơn 0",
                    item.amount(), null, null, false);
        }

        String content = item.content() != null ? item.content().trim() : "";

        // 1. Kiểm tra cú pháp NAP <MSSV>
        Matcher napMatcher = NAP_PATTERN.matcher(content);
        if (napMatcher.find()) {
            String studentCode = napMatcher.group(1).toUpperCase(Locale.ROOT);
            UUID idempotencyKey = UUID.nameUUIDFromBytes(("WEBHOOK:NAP:" + ref).getBytes(StandardCharsets.UTF_8));
            try {
                DepositCashUseCase.Result res = depositCash.deposit(
                        new DepositCashUseCase.Command(performerId, idempotencyKey, studentCode, item.amount()));
                log.info("Webhook nạp tiền thành công: studentCode={}, amount={}, replayed={}",
                        studentCode, item.amount(), res.replayed());
                return new ProcessResult(ref, "NAP", "SUCCESS",
                        "Nạp thành công " + item.amount() + "đ vào ví sinh viên " + studentCode,
                        item.amount(), studentCode, null, res.replayed());
            } catch (DomainException ex) {
                log.error("Lỗi nạp tiền từ webhook: studentCode={}, code={}", studentCode, ex.errorCode());
                return new ProcessResult(ref, "NAP", "FAILED",
                        "Lỗi nghiệp vụ: " + ex.errorCode().name() + " - " + ex.errorCode().defaultMessage(),
                        item.amount(), studentCode, null, false);
            }
        }

        // 2. Kiểm tra cú pháp TTGX <SESSION_ID>
        Matcher ttgxMatcher = TTGX_PATTERN.matcher(content);
        if (ttgxMatcher.find()) {
            long sessionId = Long.parseLong(ttgxMatcher.group(1));
            UUID idempotencyKey = UUID.nameUUIDFromBytes(("WEBHOOK:TTGX:" + ref).getBytes(StandardCharsets.UTF_8));
            try {
                PayParkingFeeByVietQrUseCase.Result res = payParkingFeeByVietQr.payByVietQr(
                        new PayParkingFeeByVietQrUseCase.Command(performerId, idempotencyKey, sessionId));
                String plate = res.vehicleOwner() != null ? res.vehicleOwner().plate().value() : "";
                String student = res.vehicleOwner() != null ? res.vehicleOwner().studentCode() : "";
                log.info("Webhook thanh toán gửi xe thành công: sessionId={}, plate={}, fee={}, replayed={}",
                        sessionId, plate, res.payment().points(), res.replayed());
                return new ProcessResult(ref, "TTGX", "SUCCESS",
                        "Thanh toán lượt gửi #" + sessionId + " thành công (xe " + plate + ")",
                        res.payment().points(), student, sessionId, res.replayed());
            } catch (DomainException ex) {
                log.error("Lỗi thanh toán vé xe từ webhook: sessionId={}, code={}", sessionId, ex.errorCode());
                return new ProcessResult(ref, "TTGX", "FAILED",
                        "Lỗi nghiệp vụ: " + ex.errorCode().name() + " - " + ex.errorCode().defaultMessage(),
                        item.amount(), null, sessionId, false);
            }
        }

        log.info("Webhook nhận được giao dịch không thuộc cú pháp NAP hoặc TTGX: ref={}, content='{}'", ref, content);
        return new ProcessResult(ref, "NONE", "IGNORED",
                "Nội dung không chứa mã NAP <MSSV> hoặc TTGX <SESSION_ID>",
                item.amount(), null, null, false);
    }
}

