package com.ptit.parking.payment.application.port.out;

/**
 * Port: ví NHẬN phí gửi xe của chủ tài khoản (system_config.receiving_wallet_id).
 * <p>R06: ví nhận do HỆ THỐNG quyết định — không bao giờ lấy từ request, không trả ra cho bảo vệ hay sinh viên.
 */
public interface ReceivingWalletProvider {

    long receivingWalletId();
}
