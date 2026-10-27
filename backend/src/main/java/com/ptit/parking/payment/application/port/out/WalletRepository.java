package com.ptit.parking.payment.application.port.out;

import com.ptit.parking.payment.domain.Wallet;

/**
 * Port: lưu ví điểm. Hiện chỉ cần tạo ví mới (SV01); các thao tác đọc/khóa ví để cộng trừ điểm
 * sẽ được bổ sung ở task ví điểm an toàn (M3-01).
 */
public interface WalletRepository {

    /** Tạo ví mới, trả về id vừa sinh. */
    long insert(Wallet wallet);
}