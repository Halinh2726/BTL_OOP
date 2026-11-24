package com.ptit.parking.payment.presentation.web;

import com.ptit.parking.manager.application.port.out.StudentDirectory;
import com.ptit.parking.payment.application.usecase.GetBankConfigUseCase;
import com.ptit.parking.payment.domain.BankAccountConfig;
import com.ptit.parking.shared.infrastructure.security.SessionUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cung cấp thông tin nạp điểm chuyển khoản VietQR cho sinh viên đang đăng nhập.
 * Đọc tài khoản ngân hàng hiện hành từ {@link GetBankConfigUseCase} (ưu tiên cấu hình Quản lý đã lưu,
 * mặc định lấy từ .env).
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
    private final GetBankConfigUseCase getBankConfig;

    public StudentDepositController(StudentDirectory students, GetBankConfigUseCase getBankConfig) {
        this.students = students;
        this.getBankConfig = getBankConfig;
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

        BankAccountConfig bank = getBankConfig.currentConfig();
        String qrCodeUrl = bank.buildVietQrUrl(amount, transferContent);

        return new DepositInfoResponse(
                studentCode,
                fullName,
                bank.bankId(),
                bank.accountNo(),
                bank.accountName(),
                transferContent,
                amount,
                qrCodeUrl);
    }
}
