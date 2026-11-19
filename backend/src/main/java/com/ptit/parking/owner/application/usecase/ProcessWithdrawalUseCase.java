package com.ptit.parking.owner.application.usecase;

import com.ptit.parking.owner.application.port.out.OwnerFinanceQuery;
import com.ptit.parking.owner.application.port.out.WithdrawalRequestRepository;
import com.ptit.parking.owner.domain.WithdrawalRequest;
import com.ptit.parking.owner.domain.WithdrawalStatus;
import com.ptit.parking.payment.application.RequestFingerprint;
import com.ptit.parking.payment.application.port.out.PointTransactionRepository;
import com.ptit.parking.payment.application.port.out.WalletRepository;
import com.ptit.parking.payment.domain.CashWithdrawal;
import com.ptit.parking.payment.domain.TransactionType;
import com.ptit.parking.payment.domain.Wallet;
import com.ptit.parking.shared.application.port.out.TransactionRunner;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Phía QUẢN LÝ của yêu cầu rút điểm: trả tiền mặt (lúc này điểm mới bị trừ), từ chối, giải quyết khiếu nại.
 * <p>Trả tiền — MỘT transaction:
 * <ol>
 *   <li>Khóa yêu cầu. Đã trả rồi (bấm hai lần, mạng gửi lại) → trả lại kết quả cũ, KHÔNG trừ lần hai (R17).</li>
 *   <li>Khóa ví chủ bãi — mọi lần trả tiền đều đi qua ví này nên các lần trả xếp hàng, kiểm tra quỹ không bị lọt.</li>
 *   <li>R16: số tiền phải trả ≤ quỹ tiền mặt quản lý đang giữ.</li>
 *   <li>{@link CashWithdrawal#execute} trừ điểm, ghi giao dịch RUT_DIEM; yêu cầu chuyển DA_TRA (R18 do domain kiểm tra).</li>
 * </ol>
 * Idempotency-Key của giao dịch được sinh CỐ ĐỊNH từ id yêu cầu: dù vì lý do gì có hai lần ghi, CSDL cũng chỉ nhận một.
 */
public class ProcessWithdrawalUseCase {

    public static final int LIST_LIMIT = 100;

    /** Kết quả trả tiền; {@code replayed}: yêu cầu đã được trả từ trước, lần này không làm gì thêm. */
    public record PaidResult(WithdrawalRequest request, boolean replayed) {}

    private final WithdrawalRequestRepository requests;
    private final OwnerFinanceQuery finance;
    private final WalletRepository wallets;
    private final PointTransactionRepository ledger;
    private final TransactionRunner transactions;
    private final Clock clock;

    public ProcessWithdrawalUseCase(WithdrawalRequestRepository requests, OwnerFinanceQuery finance,
                                    WalletRepository wallets, PointTransactionRepository ledger,
                                    TransactionRunner transactions, Clock clock) {
        this.requests = requests;
        this.finance = finance;
        this.wallets = wallets;
        this.ledger = ledger;
        this.transactions = transactions;
        this.clock = clock;
    }

    public List<WithdrawalRequest> list(WithdrawalStatus status) {
        return requests.findByStatus(status, LIST_LIMIT);
    }

    public long cashHeldByManager() {
        return finance.cashHeldByManager();
    }

    public PaidResult pay(long managerId, long requestId) {
        return transactions.inTransaction(() -> {
            WithdrawalRequest request = requests.lockById(requestId)
                    .orElseThrow(() -> new DomainException(ErrorCode.WITHDRAWAL_NOT_FOUND));
            if (request.status().isPaid()) {
                return new PaidResult(request, true);                                   // R17
            }
            if (!request.status().isPending()) {
                throw new DomainException(ErrorCode.WITHDRAWAL_STATUS_CONFLICT);
            }
            if (managerId == request.ownerId()) {
                throw new DomainException(ErrorCode.SELF_PROCESSING_FORBIDDEN);       // R18, trước khi đụng tới ví
            }
            Wallet ownerWallet = wallets.lockById(finance.receivingAccount().walletId());
            if (request.cashAmount() > finance.cashHeldByManager()) {
                throw new DomainException(ErrorCode.CASH_FUND_INSUFFICIENT);           // R16
            }
            Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            CashWithdrawal withdrawal = CashWithdrawal.execute(ownerWallet, request.points(), request.rate(),
                    managerId, keyFor(requestId), now);
            wallets.save(ownerWallet);
            long transactionId = ledger.save(withdrawal,
                    RequestFingerprint.of(TransactionType.RUT_DIEM, managerId, "request", requestId));
            request.markPaid(managerId, transactionId, now);
            return new PaidResult(requests.save(request), false);
        });
    }

    public WithdrawalRequest reject(long managerId, long requestId, String reason) {
        return transactions.inTransaction(() -> {
            WithdrawalRequest request = locked(requestId);
            request.reject(managerId, reason, clock.instant());
            return requests.save(request);
        });
    }

    public WithdrawalRequest resolveComplaint(long managerId, long requestId, String note) {
        return transactions.inTransaction(() -> {
            WithdrawalRequest request = locked(requestId);
            request.resolveComplaint(managerId, note, clock.instant());
            return requests.save(request);
        });
    }

    private WithdrawalRequest locked(long requestId) {
        return requests.lockById(requestId).orElseThrow(() -> new DomainException(ErrorCode.WITHDRAWAL_NOT_FOUND));
    }

    /** UUID cố định cho mỗi yêu cầu: "withdrawal-request:15" luôn ra cùng một khóa. */
    static UUID keyFor(long requestId) {
        return UUID.nameUUIDFromBytes(("withdrawal-request:" + requestId).getBytes(StandardCharsets.UTF_8));
    }
}
