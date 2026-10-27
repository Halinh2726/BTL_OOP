package com.ptit.parking.payment.application.port.out;

import com.ptit.parking.payment.domain.Wallet;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Port: đọc, KHÓA và lưu ví điểm.
 * <p>Quy tắc dùng khi đổi số dư: mở transaction → {@code lock...} → {@code credit/debit} trên đối tượng
 * {@link Wallet} → {@link #save}. Ví đã khóa thì request khác muốn khóa cùng ví phải ĐỢI tới khi transaction
 * này kết thúc, nên hai request đồng thời không thể cùng đọc một số dư cũ rồi cùng ghi đè lên nhau.
 */
public interface WalletRepository {

    /** Tạo ví mới, trả về id vừa sinh. */
    long insert(Wallet wallet);

    /** Đọc ví của một người để HIỂN THỊ (không khóa). Không dùng kết quả này để cộng/trừ điểm. */
    Optional<Wallet> findByUserId(long userId);

    /** Khóa một ví tới hết transaction hiện tại (SELECT ... FOR UPDATE). Chỉ gọi BÊN TRONG TransactionRunner. */
    Wallet lockById(long walletId);

    /**
     * Khóa NHIỀU ví, luôn theo id TĂNG DẦN. Hai giao dịch cùng cần ví A và B (ví dụ hai lượt thanh toán đồng thời)
     * đều khóa A trước rồi B — không thể xảy ra "mỗi bên giữ một ví và chờ nhau" (deadlock).
     */
    Map<Long, Wallet> lockInIdOrder(Collection<Long> walletIds);

    /** Ghi số dư mới của một ví ĐÃ KHÓA. */
    void save(Wallet wallet);
}