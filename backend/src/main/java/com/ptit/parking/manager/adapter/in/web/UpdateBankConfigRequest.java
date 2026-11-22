package com.ptit.parking.manager.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/** Body của PUT /api/v1/manager/bank-config. */
record UpdateBankConfigRequest(
        @NotBlank(message = "Vui lòng chọn hoặc nhập mã ngân hàng")
        @Pattern(regexp = "^[A-Z0-9]{2,20}$", message = "Mã ngân hàng gồm 2–20 ký tự chữ hoặc số (VD: MB, VCB, ACB)")
        String bankId,

        @NotBlank(message = "Vui lòng nhập số tài khoản")
        @Pattern(regexp = "^[A-Z0-9]{4,30}$", message = "Số tài khoản gồm 4–30 ký tự chữ hoặc số")
        String accountNo,

        @NotBlank(message = "Vui lòng nhập tên chủ tài khoản")
        @Size(max = 100, message = "Tên chủ tài khoản tối đa 100 ký tự")
        String accountName,

        @Pattern(regexp = "^(compact2|compact|qr_only|print)$",
                message = "Mẫu QR chỉ hỗ trợ: compact2, compact, qr_only, print")
        String template
) {
    /** Chuẩn hóa trước khi kiểm tra: bỏ khoảng trắng/dấu gạch, viết hoa mã NH & STK & tên chủ TK. */
    UpdateBankConfigRequest {
        bankId = bankId == null ? null : bankId.strip().toUpperCase(Locale.ROOT);
        accountNo = accountNo == null ? null : accountNo.strip().replaceAll("[\\s\\-.]", "").toUpperCase(Locale.ROOT);
        accountName = accountName == null ? null : accountName.strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        template = (template == null || template.isBlank()) ? "compact2" : template.strip().toLowerCase(Locale.ROOT);
    }
}
