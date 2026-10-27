package com.ptit.parking.manager.application.port.out;

/**
 * Port: chủ tài khoản — người nhận phí gửi xe, chủ của ví system_config.receiving_wallet_id.
 * <p>R09: khi rút điểm, ví nguồn LUÔN là ví này — hệ thống tự xác định, không bao giờ nhận từ request.
 */
public interface OwnerAccountDirectory {

    record OwnerAccount(long userId, String fullName, long walletId) {}

    OwnerAccount ownerAccount();
}
