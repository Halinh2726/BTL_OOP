package com.ptit.parking.manager.adapter.in.web;

import com.ptit.parking.auth.domain.AccountStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Body của PATCH /manager/guards/{id}/status (Đặc tả API #22). Nhận chuỗi rồi mới đổi sang enum
 * để giá trị lạ ("ABC") báo lỗi 400 ĐÚNG trường status, thay vì lỗi "JSON hỏng" chung chung.
 */
record ChangeGuardStatusRequest(
        @NotNull(message = "Vui lòng chọn trạng thái")
        @Pattern(regexp = "HOAT_DONG|BI_KHOA", message = "Trạng thái chỉ là HOAT_DONG hoặc BI_KHOA")
        String status) {

    AccountStatus accountStatus() {
        return AccountStatus.valueOf(status);
    }
}
