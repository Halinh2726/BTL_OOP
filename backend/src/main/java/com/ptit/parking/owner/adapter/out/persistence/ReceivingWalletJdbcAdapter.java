package com.ptit.parking.owner.adapter.out.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.ptit.parking.payment.application.port.out.ReceivingWalletProvider;

/**
 * Module chủ tài khoản cung cấp ví nhận phí cho module thanh toán (R06): đọc system_config (luôn đúng 1 dòng, id = 1).
 * Đọc mỗi lần thanh toán, không lưu tạm — đổi ví nhận trong CSDL thì lần thanh toán sau dùng ngay ví mới.
 */
@Component
class ReceivingWalletJdbcAdapter implements ReceivingWalletProvider {

    private final JdbcClient jdbc;

    ReceivingWalletJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long receivingWalletId() {
        return jdbc.sql("SELECT receiving_wallet_id FROM system_config WHERE id = 1")
                .query(Long.class)
                .single();
    }
}
