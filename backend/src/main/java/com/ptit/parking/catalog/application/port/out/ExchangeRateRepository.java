package com.ptit.parking.catalog.application.port.out;

import com.ptit.parking.payment.domain.ExchangeRate;

/**
 * Port: đọc tỷ lệ quy đổi hiện hành. Chỉ trả {@link ExchangeRate} — KHÔNG có cách nào lấy ví nhận phí
 * qua port này (R06). Sau này nghiệp vụ đổi/rút điểm của quản lý dùng lại port này.
 */
public interface ExchangeRateRepository {

    ExchangeRate currentExchangeRate();
}
