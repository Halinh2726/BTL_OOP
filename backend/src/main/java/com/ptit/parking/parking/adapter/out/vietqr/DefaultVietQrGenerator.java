package com.ptit.parking.parking.adapter.out.vietqr;

import com.ptit.parking.parking.application.port.out.VietQrGenerator;
import com.ptit.parking.parking.domain.VietQrOrder;

/**
 * Cài đặt mặc định của {@link VietQrGenerator} sinh link ảnh VietQR Napas247 theo cấu hình bãi xe.
 */
public class DefaultVietQrGenerator implements VietQrGenerator {

    private final String bankId;
    private final String accountNo;
    private final String accountName;
    private final String template;

    public DefaultVietQrGenerator(String bankId, String accountNo, String accountName, String template) {
        this.bankId = bankId;
        this.accountNo = accountNo;
        this.accountName = accountName;
        this.template = template;
    }

    @Override
    public VietQrOrder generate(long sessionId, String plateNumber, String studentCode, String fullName, long fee) {
        return VietQrOrder.create(sessionId, plateNumber, studentCode, fullName, fee,
                bankId, accountNo, accountName, template);
    }
}

