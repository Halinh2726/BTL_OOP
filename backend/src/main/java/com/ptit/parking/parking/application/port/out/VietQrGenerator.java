package com.ptit.parking.parking.application.port.out;

import com.ptit.parking.parking.domain.VietQrOrder;

/**
 * Cổng (Port) sinh đơn thanh toán và mã ảnh VietQR cho lượt gửi xe.
 */
public interface VietQrGenerator {

    /**
     * Sinh đơn thanh toán VietQR động cho một lượt gửi xe.
     */
    VietQrOrder generate(long sessionId, String plateNumber, String studentCode, String fullName, long fee);
}

