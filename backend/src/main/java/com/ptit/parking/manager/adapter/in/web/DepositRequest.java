package com.ptit.parking.manager.adapter.in.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Body của POST /manager/deposits (Đặc tả API #17). Giới hạn 10.000.000 đ mỗi lần để chặn gõ nhầm số;
 * số tiền ≤ 0 hoặc không chia hết cho tỷ lệ quy đổi là lỗi NGHIỆP VỤ (R02 → INVALID_AMOUNT), do domain kiểm tra.
 */
record DepositRequest(
        @NotBlank(message = "Vui lòng nhập MSSV")
        String studentCode,

        @NotNull(message = "Vui lòng nhập số tiền")
        @Max(value = 10_000_000, message = "Tối đa 10.000.000 đ mỗi lần")
        Long cashAmount) {
}