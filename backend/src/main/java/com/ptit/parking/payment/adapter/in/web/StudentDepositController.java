package com.ptit.parking.payment.adapter.in.web;

import com.ptit.parking.manager.application.port.out.StudentDirectory;
import com.ptit.parking.shared.security.SessionUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cung cấp thông tin nạp điểm chuyển khoản VietQR cho sinh viên đang đăng nhập.
 */
@RestController
@RequestMapping("/api/v1/me")
public class StudentDepositController {

    public record DepositInfoResponse(
            String studentCode,
            String fullName,
            String bankId,
            String accountNo,
            String accountName,
            String transferContent,
            long amount,
            String qrCodeUrl
    ) {}

    private final StudentDirectory students;
    private final String bankId;
    private final String accountNo;
    private final String accountName;
    private final String template;

    public StudentDepositController(
            StudentDirectory students,
            @Value("${parking.vietqr.bank-id:MB}") String bankId,
            @Value("${parking.vietqr.account-no:0987654321}") String accountNo,
            @Value("${parking.vietqr.account-name:BAI XE HOC VIEN PTIT}") String accountName,
            @Value("${parking.vietqr.template:compact2}") String template) {
        this.students = students;
        this.bankId = bankId;
        this.accountNo = accountNo;
        this.accountName = accountName;
        this.template = template;
    }

    @GetMapping("/deposit-info")
    public DepositInfoResponse depositInfo(
            @AuthenticationPrincipal SessionUser user,
            @RequestParam(defaultValue = "50000")
            @Min(value = 1000, message = "Số tiền nạp tối thiểu là 1.000đ")
            @Max(value = 50000000, message = "Số tiền nạp tối đa là 50.000.000đ") long amount) {
        StudentDirectory.StudentAccount student = students.findByUserId(user.id()).orElse(null);
        String studentCode = student != null ? student.studentCode() : ("USER" + user.id());
        String fullName = student != null ? student.fullName() : "Người dùng #" + user.id();
        String transferContent = "NAP " + studentCode;

        String encodedContent = URLEncoder.encode(transferContent, StandardCharsets.UTF_8);
        String encodedName = URLEncoder.encode(accountName, StandardCharsets.UTF_8);
        String qrCodeUrl = String.format(
                "https://img.vietqr.io/image/%s-%s-%s.png?amount=%d&addInfo=%s&accountName=%s",
                bankId, accountNo, template, amount, encodedContent, encodedName);

        return new DepositInfoResponse(
                studentCode, fullName, bankId, accountNo, accountName, transferContent, amount, qrCodeUrl);
    }
}

