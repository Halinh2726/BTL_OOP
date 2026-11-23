package com.ptit.parking.parking.adapter.out.vietqr;

import com.ptit.parking.parking.application.port.out.VietQrGenerator;
import com.ptit.parking.parking.domain.VietQrOrder;
import com.ptit.parking.payment.domain.BankAccountConfig;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Cài đặt mặc định của {@link VietQrGenerator} sinh link ảnh VietQR Napas247 theo cấu hình bãi xe
 * (đọc động theo cấu hình Quản lý đã lưu hoặc mặc định từ .env).
 */
public class DefaultVietQrGenerator implements VietQrGenerator {

    private final Supplier<BankAccountConfig> configSupplier;

    public DefaultVietQrGenerator(Supplier<BankAccountConfig> configSupplier) {
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier");
    }

    public DefaultVietQrGenerator(String bankId, String accountNo, String accountName, String template) {
        BankAccountConfig fixed = new BankAccountConfig(bankId, accountNo, accountName, template);
        this.configSupplier = () -> fixed;
    }

    @Override
    public VietQrOrder generate(long sessionId, String plateNumber, String studentCode, String fullName, long fee) {
        BankAccountConfig cfg = configSupplier.get();
        return VietQrOrder.create(sessionId, plateNumber, studentCode, fullName, fee,
                cfg.bankId(), cfg.accountNo(), cfg.accountName(), cfg.template());
    }
}
