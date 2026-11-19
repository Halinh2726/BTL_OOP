package com.ptit.parking.support.application;

import com.ptit.parking.payment.application.RequestFingerprint;
import com.ptit.parking.payment.application.port.out.PointTransactionRepository;
import com.ptit.parking.payment.application.port.out.ReceivingWalletProvider;
import com.ptit.parking.payment.application.port.out.WalletRepository;
import com.ptit.parking.payment.domain.PointAdjustment;
import com.ptit.parking.payment.domain.TransactionType;
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Trả điểm từ ví CHỦ BÃI XE sang ví sinh viên bằng một giao dịch điều chỉnh ({@link PointAdjustment}) — dùng cho
 * hoàn phí thu sai và bồi thường. Không tạo ra điểm mới (R12): chỉ chuyển giữa hai ví, khóa hai ví theo id tăng dần.
 * <p>PHẢI gọi bên trong transaction của use case, sau khi đã khóa khiếu nại: tiền và trạng thái khiếu nại cùng được
 * ghi hoặc cùng bị hủy. Khóa chống trùng sinh từ {@code purpose} (ví dụ "complaint-refund:12") nên mỗi khiếu nại chỉ
 * trả được một lần kể cả khi có lỗi lặp.
 */
public class PointPayout {

    private final WalletRepository wallets;
    private final ReceivingWalletProvider ownerWallet;
    private final PointTransactionRepository ledger;

    public PointPayout(WalletRepository wallets, ReceivingWalletProvider ownerWallet, PointTransactionRepository ledger) {
        this.wallets = wallets;
        this.ownerWallet = ownerWallet;
        this.ledger = ledger;
    }

    /** @return id giao dịch điều chỉnh vừa ghi */
    public long payStudent(long studentId, long points, String reason, Long originalTransactionId, long performerId,
                           String purpose, Instant now) {
        long studentWalletId = wallets.findByUserId(studentId)
                .orElseThrow(() -> new DomainException(ErrorCode.STUDENT_NOT_FOUND)).id();
        long ownerWalletId = ownerWallet.receivingWalletId();
        Map<Long, Wallet> locked = wallets.lockInIdOrder(List.of(ownerWalletId, studentWalletId));
        Wallet source = locked.get(ownerWalletId);
        Wallet target = locked.get(studentWalletId);
        UUID key = UUID.nameUUIDFromBytes(purpose.getBytes(StandardCharsets.UTF_8));
        PointAdjustment adjustment = PointAdjustment.execute(source, target, points, reason, originalTransactionId,
                performerId, key, now);
        wallets.save(source);
        wallets.save(target);
        return ledger.save(adjustment, RequestFingerprint.of(TransactionType.DIEU_CHINH, performerId, purpose, points));
    }
}
