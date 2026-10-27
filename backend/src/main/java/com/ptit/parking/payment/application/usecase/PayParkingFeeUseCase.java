package com.ptit.parking.payment.application.usecase;

import com.ptit.parking.payment.application.port.out.ReceivingWalletProvider;
import com.ptit.parking.payment.application.port.out.WalletRepository;
import com.ptit.parking.payment.domain.ParkingPayment;
import com.ptit.parking.payment.domain.Wallet;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HT01 — tự động thanh toán phí gửi xe: trừ ví sinh viên, cộng ví nhận phí của chủ tài khoản. Không có tác nhân
 * nào gọi trực tiếp: chỉ chạy khi BV02 (xác nhận xe ra) «include» nó, BÊN TRONG transaction của bên gọi —
 * thanh toán lỗi thì lượt gửi cũng không được đóng.
 * <p>Khóa HAI ví theo id tăng dần (Đặc tả API mục 8): hai lượt thanh toán song song luôn khóa theo cùng thứ tự
 * nên không thể chờ vòng lẫn nhau (deadlock).
 */
public class PayParkingFeeUseCase {

    /** {@code fee} do hệ thống tính (FeePolicy) và ví nhận lấy từ cấu hình — không cái nào đến từ request (R06). */
    public record Command(long sessionId, long studentUserId, long fee, long guardId,
                          UUID idempotencyKey, Instant paidAt) {}

    private final WalletRepository wallets;
    private final ReceivingWalletProvider receivingWallet;

    public PayParkingFeeUseCase(WalletRepository wallets, ReceivingWalletProvider receivingWallet) {
        this.wallets = wallets;
        this.receivingWallet = receivingWallet;
    }

    /** Trả về giao dịch CHƯA lưu — bên gọi lưu nó (qua IdempotencyGuard) trong cùng transaction. */
    public ParkingPayment pay(Command command) {
        long studentWalletId = wallets.findByUserId(command.studentUserId()).orElseThrow().id();
        long receiverWalletId = receivingWallet.receivingWalletId();

        Map<Long, Wallet> locked = wallets.lockInIdOrder(List.of(studentWalletId, receiverWalletId));
        Wallet student = locked.get(studentWalletId);
        Wallet receiver = locked.get(receiverWalletId);

        // Không đủ điểm -> ném lỗi TRƯỚC khi cộng cho chủ tài khoản; chưa có gì được lưu
        ParkingPayment payment = ParkingPayment.execute(command.sessionId(), student, receiver, command.fee(),
                command.guardId(), command.idempotencyKey(), command.paidAt());
        wallets.save(student);
        wallets.save(receiver);
        return payment;
    }
}
