package com.ptit.parking.manager.adapter.in.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;

/**
 * Body của POST /manager/withdrawals (Đặc tả API #19). KHÔNG có trường ví nguồn: luôn là ví chủ tài khoản (R09).
 * Giới hạn 10.000.000 điểm mỗi lần để chặn gõ nhầm số; số điểm ≤ 0 là lỗi NGHIỆP VỤ (INVALID_AMOUNT), do domain kiểm tra.
 */
record WithdrawRequest(
        @NotNull(message = "Vui lòng nhập số điểm")
        @Max(value = 10_000_000, message = "Tối đa 10.000.000 điểm mỗi lần")
        Long points) {
}
